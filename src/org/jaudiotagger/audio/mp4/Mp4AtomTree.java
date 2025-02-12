package org.jaudiotagger.audio.mp4;

import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.NullBoxIdException;
import org.jaudiotagger.audio.mp4.atom.Mp4BoxHeader;
import org.jaudiotagger.audio.mp4.atom.Mp4MetaBox;
import org.jaudiotagger.audio.mp4.atom.Mp4StcoBox;
import org.jaudiotagger.audio.mp4.atom.NullPadding;
import org.jaudiotagger.logging.ErrorMessage;
import org.jaudiotagger.utils.tree.DefaultMutableTreeNode;
import org.jaudiotagger.utils.tree.DefaultTreeModel;
import org.jaudiotagger.utils.tree.TreeNode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;

/**
 * Tree representing atoms in the mp4 file
 *
 * Note it doesn't create the complete tree it delves into subtrees for atom we know about and are interested in. (Note
 * it would be impossible to create a complete tree for any file without understanding all the nodes because
 * some atoms such as meta contain data and children and therefore need to be specially preprocessed)
 *
 * This class is currently only used when writing tags because it better handles the difficulties of mdat and free
 * atoms being optional/multiple places then the older sequential method. It is expected this class will eventually
 * be used when reading tags as well.
 *
 * Uses a TreeModel for the tree, with convenience methods holding onto references to most common nodes so they
 * can be used without having to traverse the tree again.
 */
public class Mp4AtomTree
{
    private DefaultMutableTreeNode<Object> rootNode;
    private DefaultTreeModel<Object> dataTree;
    private DefaultMutableTreeNode<Object> moovNode;
    private DefaultMutableTreeNode<Object> mdatNode;
    private DefaultMutableTreeNode<Object> ilstNode;
    private DefaultMutableTreeNode<Object> metaNode;
    private DefaultMutableTreeNode<Object> tagsNode;
    private DefaultMutableTreeNode<Object> udtaNode;
    private DefaultMutableTreeNode<Object> hdlrWithinMdiaNode;
    private DefaultMutableTreeNode<Object> hdlrWithinMetaNode;
    private final List<DefaultMutableTreeNode<Object>> stcoNodes = new ArrayList<>();
    private final List<DefaultMutableTreeNode<Object>> freeNodes = new ArrayList<>();
//    private final List<DefaultMutableTreeNode<Object>> mdatNodes = new ArrayList<>();
    private final List<DefaultMutableTreeNode<Object>> trakNodes = new ArrayList<>();

    private final List<Mp4StcoBox> stcos = new ArrayList<>();
    private ByteBuffer moovBuffer; //Contains all the data under moov
    private Mp4BoxHeader moovHeader;

    //Logger Object
    public static final Logger logger = Logger.getLogger("org.jaudiotagger.audio.mp4");

    /**
     * Create Atom Tree
     *
     * @param raf
     * @throws IOException
     * @throws CannotReadException
     */
    public Mp4AtomTree(RandomAccessFile raf) throws IOException, CannotReadException
    {
        buildTree(raf, true);
    }

    /**
     * Create Atom Tree and maintain open channel to raf, should only be used if will continue
     * to use raf after this call, you will have to close raf yourself.
     *
     * @param raf
     * @param closeOnExit to keep randomfileaccess open, only used when randomaccessfile already being used
     * @throws IOException
     * @throws CannotReadException
     */
    public Mp4AtomTree(RandomAccessFile raf, boolean closeOnExit) throws IOException, CannotReadException
    {
        buildTree(raf, closeOnExit);
    }

    /**
     * Build a tree of the atoms in the file
     *
     * @param raf
     * @param closeExit false to keep randomfileacces open, only used when randomaccessfile already being used
     * @throws IOException
     * @throws org.jaudiotagger.audio.exceptions.CannotReadException
     */
    public void buildTree(RandomAccessFile raf, boolean closeExit) throws IOException, CannotReadException
    {
        FileChannel fc = raf.getChannel();
        try
        {
            //make sure at start of file
            fc.position(0);

            //Build up map of nodes
            rootNode = new DefaultMutableTreeNode<>();
            dataTree = new DefaultTreeModel<>(rootNode);

            //Iterate though all the top level Nodes
            ByteBuffer headerBuffer = ByteBuffer.allocate(Mp4BoxHeader.HEADER_LENGTH);
            // we need to have at least enough data in the file left
            // to read a box header
            while (fc.position() < fc.size() - Mp4BoxHeader.HEADER_LENGTH)
            {
                Mp4BoxHeader boxHeader = new Mp4BoxHeader();
                headerBuffer.clear();          
                fc.read(headerBuffer);
                headerBuffer.rewind();

                try
                {
                    boxHeader.update(headerBuffer);
                }
                catch(NullBoxIdException ne)
                {
                    //If we only get this error after all the expected data has been found we allow it
                    if(moovNode!=null&mdatNode!=null)
                    {
                        NullPadding np = new NullPadding(fc.position() - Mp4BoxHeader.HEADER_LENGTH,fc.size());
                        DefaultMutableTreeNode<Object> trailingPaddingNode = new DefaultMutableTreeNode<>(np);
                        rootNode.add(trailingPaddingNode);
                        logger.warning(ErrorMessage.NULL_PADDING_FOUND_AT_END_OF_MP4.getMsg(np.getFilePos()));
                        break;
                    }
                    else
                    {
                        //File appears invalid
                        throw ne;
                    }
                }
                                   
                boxHeader.setFilePos(fc.position() - Mp4BoxHeader.HEADER_LENGTH);
                DefaultMutableTreeNode<Object> newAtom = new DefaultMutableTreeNode<>(boxHeader);

                //Go down moov
                if (boxHeader.getId().equals(Mp4AtomIdentifier.MOOV.getFieldName()))
                {
                    //A second Moov atom, this is illegal but may just be mess at the end of the file so ignore
                    //and finish
                    if(moovNode!=null&mdatNode!=null)
                    {
                        logger.warning(ErrorMessage.ADDITIONAL_MOOV_ATOM_AT_END_OF_MP4.getMsg(fc.position() - Mp4BoxHeader.HEADER_LENGTH));
                        break;
                    }
                    moovNode    = newAtom;
                    moovHeader  = boxHeader;

                    long filePosStart = fc.position();
                    moovBuffer = ByteBuffer.allocate(boxHeader.getDataLength());
                    int bytesRead = fc.read(moovBuffer);

                    //If Moov atom is incomplete we are not going to be able to read this file properly
                    if(bytesRead < boxHeader.getDataLength())
                    {
                        String msg = ErrorMessage.ATOM_LENGTH_LARGER_THAN_DATA.getMsg(boxHeader.getId(), boxHeader.getDataLength(),bytesRead);
                        throw new CannotReadException(msg);
                    }
                    moovBuffer.rewind();
                    buildChildrenOfNode(moovBuffer, newAtom);
                    fc.position(filePosStart);
                }
                else if (boxHeader.getId().equals(Mp4AtomIdentifier.FREE.getFieldName()))
                {
                    //Might be multiple in different locations
                    freeNodes.add(newAtom);
                }
                else if (boxHeader.getId().equals(Mp4AtomIdentifier.MDAT.getFieldName()))
                {
                    //mdatNode always points to the last mDatNode, normally there is just one mdatnode but do have
                    //a valid example of multiple mdatnode

                    //if(mdatNode!=null)
                    //{
                    //    throw new CannotReadException(ErrorMessage.MP4_FILE_CONTAINS_MULTIPLE_DATA_ATOMS.getMsg());
                    //}
                    mdatNode = newAtom;
//                    mdatNodes.add(newAtom);
                }
                rootNode.add(newAtom);

                //64bit data length
                if(boxHeader.getLength() == 1)
                {
                    ByteBuffer data64bitLengthBuffer = ByteBuffer.allocate(Mp4BoxHeader.DATA_64BITLENGTH);
                    data64bitLengthBuffer.order(ByteOrder.BIG_ENDIAN);
                    int  bytesRead = fc.read(data64bitLengthBuffer);
                    if (bytesRead != Mp4BoxHeader.DATA_64BITLENGTH)
                    {
                        return;
                    }
                    data64bitLengthBuffer.rewind();
                    long length = data64bitLengthBuffer.getLong();
                    if (length < Mp4BoxHeader.HEADER_LENGTH){
                        return;
                    }

                    fc.position(fc.position() + length - Mp4BoxHeader.REALDATA_64BITLENGTH);
                }
                else
                {
                    fc.position(fc.position() + boxHeader.getDataLength());
                }
            }
            long extraDataLength = fc.size() - fc.position();
            if (extraDataLength != 0) {
                logger.warning(ErrorMessage.EXTRA_DATA_AT_END_OF_MP4.getMsg(extraDataLength));
            }

            //If we cant find the audio then we cannot modify this file so better to throw exception
            //now rather than later when try and write to it.
            if(mdatNode==null)
            {
                throw new CannotReadException(ErrorMessage.MP4_CANNOT_FIND_AUDIO.getMsg());
            }
        }
        finally
        {

            if (closeExit)
            {
                fc.close();
            }
        }
    }

    /**
     * Display atom tree
     */
    public void printAtomTree()
    {
        Enumeration<TreeNode<Object>> e = rootNode.preorderEnumeration();
        DefaultMutableTreeNode<Object> nextNode;
        while (e.hasMoreElements())
        {
            nextNode = (DefaultMutableTreeNode<Object>)e.nextElement();
            Mp4BoxHeader header = (Mp4BoxHeader) nextNode.getUserObject();
            if (header != null)
            {
                StringBuilder tabbing = new StringBuilder();
                for (int i = 1; i < nextNode.getLevel(); i++)
                {
                    tabbing.append("\t");
                }

                if(header instanceof NullPadding)
                {
                    if(header.getLength()==1)
                    {
                        System.out.println(tabbing + "Null pad " + " @ " + header.getFilePos() + " 64bitDataSize" + " ,ends @ " + (header.getFilePos() + header.getLength()));
                    }
                    else
                    {
                        System.out.println(tabbing + "Null pad " + " @ " + header.getFilePos() + " of size:" + header.getLength() + " ,ends @ " + (header.getFilePos() + header.getLength()));
                    }
                }
                else
                {
                    if(header.getLength()==1)
                    {
                        System.out.println(tabbing + "Atom " + header.getId() + " @ " + header.getFilePos() + " 64BitDataSize"  + " ,ends @ " + (header.getFilePos() + header.getLength()));
                    }
                    else
                    {
                        System.out.println(tabbing + "Atom " + header.getId() + " @ " + header.getFilePos() + " of size:" + header.getLength() + " ,ends @ " + (header.getFilePos() + header.getLength()));
                    }
                }
            }
        }
    }

    /**
     *
     * @param moovBuffer
     * @param parentNode
     * @throws CannotReadException
     */
    public void buildChildrenOfNode(ByteBuffer moovBuffer, DefaultMutableTreeNode<Object> parentNode) throws CannotReadException
    {
        Mp4BoxHeader boxHeader;

        //Preprocessing for nodes that contain data before their children atoms
        Mp4BoxHeader parentBoxHeader = (Mp4BoxHeader) parentNode.getUserObject();

        //We set the buffers position back to this after processing the children
        int justAfterHeaderPos = moovBuffer.position();

        //Preprocessing for meta that normally contains 4 data bytes, but doesn't where found under track or tags atom
        if (parentBoxHeader.getId().equals(Mp4AtomIdentifier.META.getFieldName()))
        {
            Mp4MetaBox meta = new Mp4MetaBox(parentBoxHeader, moovBuffer);
            meta.processData();

            try
            {
                /*boxHeader = */new Mp4BoxHeader(moovBuffer);
            }
            catch(NullBoxIdException nbe)
            {
                //It might be that the meta box didn't actually have any additional data after it so we adjust the buffer
                //to be immediately after metabox and code can retry
                moovBuffer.position(moovBuffer.position() - Mp4MetaBox.FLAGS_LENGTH);
            }
            finally
            {
                //Skip back last header cos this was only a test 
                moovBuffer.position(moovBuffer.position()-  Mp4BoxHeader.HEADER_LENGTH);
            }
        }

        //Defines where to start looking for the first child node
        int startPos = moovBuffer.position();        
        while (moovBuffer.position() < ((startPos + parentBoxHeader.getDataLength()) - Mp4BoxHeader.HEADER_LENGTH))
        {
            boxHeader = new Mp4BoxHeader(moovBuffer);
//            if (boxHeader != null)
            {
                boxHeader.setFilePos(moovHeader.getFilePos() + moovBuffer.position());
                logger.finest("Atom " + boxHeader.getId() + " @ " + boxHeader.getFilePos() + " of size:" + boxHeader.getLength() + " ,ends @ " + (boxHeader.getFilePos() + boxHeader.getLength()));
                DefaultMutableTreeNode<Object> newAtom = new DefaultMutableTreeNode<>(boxHeader);
                parentNode.add(newAtom);

                if (boxHeader.getId().equals(Mp4AtomIdentifier.UDTA.getFieldName()))
                {
                    udtaNode = newAtom;
                }
                //only interested in metaNode that is child of udta node
                else if (boxHeader.getId().equals(Mp4AtomIdentifier.META.getFieldName())&&parentBoxHeader.getId().equals(Mp4AtomIdentifier.UDTA.getFieldName()))
                {
                    metaNode = newAtom;
                }
                else if (boxHeader.getId().equals(Mp4AtomIdentifier.HDLR.getFieldName())&&parentBoxHeader.getId().equals(Mp4AtomIdentifier.META.getFieldName()))
                {
                    hdlrWithinMetaNode = newAtom;
                }
                else if (boxHeader.getId().equals(Mp4AtomIdentifier.HDLR.getFieldName()))
                {
                    hdlrWithinMdiaNode = newAtom;
                }
                else if (boxHeader.getId().equals(Mp4AtomIdentifier.TAGS.getFieldName()))
                {
                    tagsNode = newAtom;
                }
                else if (boxHeader.getId().equals(Mp4AtomIdentifier.STCO.getFieldName()))
                {
                    stcos.add(new Mp4StcoBox(boxHeader, moovBuffer));
                    stcoNodes.add(newAtom);
                }
                else if (boxHeader.getId().equals(Mp4AtomIdentifier.ILST.getFieldName()))
                {
                    DefaultMutableTreeNode<Object> parent = (DefaultMutableTreeNode<Object>)parentNode.getParent();
                    if(parent!=null)
                    {
                        Mp4BoxHeader parentsParent = (Mp4BoxHeader)(parent).getUserObject();
                        if(parentsParent!=null)
                        {
                            if(parentBoxHeader.getId().equals(Mp4AtomIdentifier.META.getFieldName())&&parentsParent.getId().equals(Mp4AtomIdentifier.UDTA.getFieldName()))
                            {
                                ilstNode = newAtom;
                            }
                        }
                    }    
                }
                else if (boxHeader.getId().equals(Mp4AtomIdentifier.FREE.getFieldName()))
                {
                    //Might be multiple in different locations
                    freeNodes.add(newAtom);
                }
                else if (boxHeader.getId().equals(Mp4AtomIdentifier.TRAK.getFieldName()))
                {
                    //Might be multiple in different locations, although only one should be audio track
                    trakNodes.add(newAtom);
                }

                //For these atoms iterate down to build their children
                if ((boxHeader.getId().equals(Mp4AtomIdentifier.TRAK.getFieldName())) ||
                        (boxHeader.getId().equals(Mp4AtomIdentifier.MDIA.getFieldName())) ||
                        (boxHeader.getId().equals(Mp4AtomIdentifier.MINF.getFieldName())) ||
                        (boxHeader.getId().equals(Mp4AtomIdentifier.STBL.getFieldName())) ||
                        (boxHeader.getId().equals(Mp4AtomIdentifier.UDTA.getFieldName())) ||
                        (boxHeader.getId().equals(Mp4AtomIdentifier.META.getFieldName())) ||
                        (boxHeader.getId().equals(Mp4AtomIdentifier.ILST.getFieldName())))
                {                
                    buildChildrenOfNode(moovBuffer, newAtom);
                }
                //Now  adjust buffer for the next atom header at this level
                moovBuffer.position(moovBuffer.position() + boxHeader.getDataLength());

            }
        }
        moovBuffer.position(justAfterHeaderPos);
    }


    /**
     *
     * @return
     */
    public DefaultTreeModel<Object> getDataTree()
    {
        return dataTree;
    }


    /**
     *
     * @return
     */
    public DefaultMutableTreeNode<Object> getMoovNode()
    {
        return moovNode;
    }

    /**
     *
     * @return
     */
    public List<DefaultMutableTreeNode<Object>> getStcoNodes()
    {
        return stcoNodes;
    }

    /**
     *
     * @return
     */
    public DefaultMutableTreeNode<Object> getIlstNode()
    {
        return ilstNode;
    }

    /**
     *
     * @param node
     * @return
     */
    public Mp4BoxHeader getBoxHeader(DefaultMutableTreeNode<Object> node)
    {
        if (node == null)
        {
            return null;
        }
        return (Mp4BoxHeader) node.getUserObject();
    }

    /**
     *
     * @return
     */
    public DefaultMutableTreeNode<Object> getMdatNode()
    {
        return mdatNode;
    }

    /**
     *
     * @return
     */
    public DefaultMutableTreeNode<Object> getUdtaNode()
    {
        return udtaNode;
    }

    /**
     *
     * @return
     */
    public DefaultMutableTreeNode<Object> getMetaNode()
    {
        return metaNode;
    }

    /**
     *
     * @return
     */
    public DefaultMutableTreeNode<Object> getHdlrWithinMetaNode()
    {
        return hdlrWithinMetaNode;
    }

    /**
     *
     * @return
     */
    public DefaultMutableTreeNode<Object> getHdlrWithinMdiaNode()
    {
        return hdlrWithinMdiaNode;
    }

    /**
     *
     * @return
     */
    public DefaultMutableTreeNode<Object> getTagsNode()
    {
        return tagsNode;
    }

    /**
     *
     * @return
     */
    public List<DefaultMutableTreeNode<Object>> getFreeNodes()
    {
        return freeNodes;
    }

    /**
     *
     * @return
     */
    public List<DefaultMutableTreeNode<Object>> getTrakNodes()
    {
        return trakNodes;
    }

    /**
     *
     * @return
     */
    public List<Mp4StcoBox> getStcos()
    {
        return stcos;
    }

    /**
     *
     * @return
     */
    public ByteBuffer getMoovBuffer()
    {
        return moovBuffer;
    }

    /**
     *
     * @return
     */
    public Mp4BoxHeader getMoovHeader()
    {
        return moovHeader;
    }
}
