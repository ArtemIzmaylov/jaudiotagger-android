package org.jaudiotagger.audio.mp3;
/**
 *  @author : Paul Taylor
 *  @author : Eric Farng
 *
 *  Version @version:$Id$
 *
 *  MusicTag Copyright (C)2003,2004
 *
 *  This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser
 *  General Public  License as published by the Free Software Foundation; either version 2.1 of the License,
 *  or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 *  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License along with this library; if not,
 *  you can get a copy from http://www.opensource.org/licenses/lgpl-license.php or write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 */

import androidx.annotation.NonNull;

import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.generic.AudioFileWithCommonTags;
import org.jaudiotagger.logging.AbstractTagDisplayFormatter;
import org.jaudiotagger.logging.ErrorMessage;
import org.jaudiotagger.logging.Hex;
import org.jaudiotagger.logging.PlainTextTagDisplayFormatter;
import org.jaudiotagger.logging.XMLTagDisplayFormatter;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagNotFoundException;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.AbstractTag;
import org.jaudiotagger.tag.id3.ID3v1Tag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * This class represents a physical MP3 File
 */
public class MP3File extends AudioFileWithCommonTags
{
    private static final int MINIMUM_FILESIZE = 150;

    protected static AbstractTagDisplayFormatter tagFormatter;
    /**
     * Creates a new empty MP3File datatype that is not associated with a
     * specific file.
     */
    public MP3File()
    {
        super();
    }

    /**
     * Creates a new MP3File datatype and parse the tag from the given filename.
     *
     * @param filename MP3 file
     * @throws IOException  on any I/O error
     * @throws TagException on any exception generated by this library.
     * @throws org.jaudiotagger.audio.exceptions.ReadOnlyFileException
     * @throws org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
     */
    public MP3File(String filename) throws IOException, TagException, ReadOnlyFileException, CannotReadException, InvalidAudioFrameException
    {
        this(new File(filename));
    }

    /**
     * Creates a new MP3File dataType and parse the tag from the given file
     * Object, files must be writable to use this constructor.
     *
     * @param file        MP3 file
     * @param loadOptions decide what tags to load
     * @throws IOException  on any I/O error
     * @throws TagException on any exception generated by this library.
     * @throws org.jaudiotagger.audio.exceptions.ReadOnlyFileException
     * @throws org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
     */
    public MP3File(File file, int loadOptions) throws IOException, TagException, ReadOnlyFileException, CannotReadException, InvalidAudioFrameException
    {
        this(file, loadOptions, false);
    }

    /**
     *
     * @param startByte
     * @param endByte
     * @return
     * @throws Exception
     *
     * @return true if all the bytes between in the file between startByte and endByte are null, false
     * otherwise
     */
    private boolean isFilePortionNull(int startByte, int endByte) throws IOException
    {
        logger.config("Checking file portion:" + Hex.asHex(startByte) + ":" + Hex.asHex(endByte));
        try (FileInputStream fis = new FileInputStream(file); FileChannel fc = fis.getChannel())
        {
            fc.position(startByte);
            ByteBuffer bb = ByteBuffer.allocateDirect(endByte - startByte);
            fc.read(bb);
            while (bb.hasRemaining())
            {
                if (bb.get() != 0)
                {
                    return false;
                }
            }
        }
        return true;
    }
    /**
     * Regets the audio header starting from start of file, and write appropriate logging to indicate
     * potential problem to user.
     *
     * @param startByte
     * @param firstHeaderAfterTag
     * @return
     * @throws IOException
     * @throws InvalidAudioFrameException
     */
    private MP3AudioHeader checkAudioStart(long startByte, MP3AudioHeader firstHeaderAfterTag) throws IOException, InvalidAudioFrameException
    {
        MP3AudioHeader headerOne;
        MP3AudioHeader headerTwo;

        logger.warning(ErrorMessage.MP3_ID3TAG_LENGTH_INCORRECT.getMsg(file.getPath(), Hex.asHex(startByte), Hex.asHex(firstHeaderAfterTag.getMp3StartByte())));

        //because we cant agree on start location we reread the audioheader from the start of the file, at least
        //this way we cant overwrite the audio although we might overwrite part of the tag if we write this file
        //back later
        headerOne = new MP3AudioHeader(file, 0);
        logger.config("Checking from start:" + headerOne);

        //Although the id3 tag size appears to be incorrect at least we have found the same location for the start
        //of audio whether we start searching from start of file or at the end of the alleged of file so no real
        //problem
        if (firstHeaderAfterTag.getMp3StartByte() == headerOne.getMp3StartByte())
        {
            logger.config(ErrorMessage.MP3_START_OF_AUDIO_CONFIRMED.getMsg(file.getPath(),
                    Hex.asHex(headerOne.getMp3StartByte())));
            return firstHeaderAfterTag;
        }
        else
        {

            //We get a different value if read from start, can't guarantee 100% correct lets do some more checks
            logger.config((ErrorMessage.MP3_RECALCULATED_POSSIBLE_START_OF_MP3_AUDIO.getMsg(file.getPath(),
                            Hex.asHex(headerOne.getMp3StartByte()))));

            //Same frame count so probably both audio headers with newAudioHeader being the first one
            if (firstHeaderAfterTag.getNumberOfFrames() == headerOne.getNumberOfFrames())
            {
                logger.warning((ErrorMessage.MP3_RECALCULATED_START_OF_MP3_AUDIO.getMsg(file.getPath(),
                                Hex.asHex(headerOne.getMp3StartByte()))));
                return headerOne;
            }

            //If the size reported by the tag header is a little short and there is only nulls between the recorded value
            //and the start of the first audio found then we stick with the original header as more likely that currentHeader
            //DataInputStream not really a header
            if(isFilePortionNull((int) startByte,(int) firstHeaderAfterTag.getMp3StartByte()))
            {
                return firstHeaderAfterTag;
            }

            //Skip to the next header (header 2, counting from start of file)
            headerTwo = new MP3AudioHeader(file, headerOne.getMp3StartByte()
                    + headerOne.mp3FrameHeader.getFrameLength());

            //It matches the header we found when doing the original search from after the ID3Tag therefore it
            //seems that newAudioHeader was a false match and the original header was correct
            if (headerTwo.getMp3StartByte() == firstHeaderAfterTag.getMp3StartByte())
            {
                logger.warning((ErrorMessage.MP3_START_OF_AUDIO_CONFIRMED.getMsg(file.getPath(),
                                Hex.asHex(firstHeaderAfterTag.getMp3StartByte()))));
                return firstHeaderAfterTag;
            }

            //It matches the frameCount the header we just found so lends weight to the fact that the audio does indeed start at new header
            //however it maybe that neither are really headers and just contain the same data being misrepresented as headers.
            if (headerTwo.getNumberOfFrames() == headerOne.getNumberOfFrames())
            {
                logger.warning((ErrorMessage.MP3_RECALCULATED_START_OF_MP3_AUDIO.getMsg(file.getPath(),
                                Hex.asHex(headerOne.getMp3StartByte()))));
                return headerOne;
            }
            ///Doesnt match the frameCount lets go back to the original header
            else
            {
                logger.warning((ErrorMessage.MP3_RECALCULATED_START_OF_MP3_AUDIO.getMsg(file.getPath(),
                                Hex.asHex(firstHeaderAfterTag.getMp3StartByte()))));
                return firstHeaderAfterTag;
            }
        }
    }

    /**
     * Creates a new MP3File dataType and parse the tag from the given file
     * Object, files can be opened read only if required.
     *
     * @param file        MP3 file
     * @param loadOptions decide what tags to load
     * @param readOnly    causes the files to be opened readonly
     * @throws IOException  on any I/O error
     * @throws TagException on any exception generated by this library.
     * @throws org.jaudiotagger.audio.exceptions.ReadOnlyFileException
     * @throws org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
     */
    public MP3File(File file, int loadOptions, boolean readOnly) throws IOException, TagException, ReadOnlyFileException, CannotReadException, InvalidAudioFrameException
    {
        super(file, readOnly, loadOptions);
    }

    /**
     * Used by tags when writing to calculate the location of the music file
     *
     * @param file
     * @return the location within the file that the audio starts
     * @throws java.io.IOException
     * @throws org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
     */
    public long getMP3StartByte(File file) throws InvalidAudioFrameException, IOException
    {
        //Read ID3v2 tag size (if tag exists) to allow audio header parsing to skip over tag
        long startByte = AbstractID3v2Tag.getV2TagSizeIfExists(file);

        MP3AudioHeader audioHeader = new MP3AudioHeader(file, startByte);
        if (startByte != audioHeader.getMp3StartByte())
        {
            logger.config("First header found after tag:" + audioHeader);
            audioHeader = checkAudioStart(startByte, audioHeader);
        }
        return audioHeader.getMp3StartByte();
    }

    /**
     * Extracts the raw ID3v2 tag data into a file.
     *
     * This provides access to the raw data before manipulation, the data is written from the start of the file
     * to the start of the Audio Data. This is primarily useful for manipulating corrupted tags that are not
     * (fully) loaded using the standard methods.
     *
     * @param outputFile to write the data to
     * @return
     * @throws TagNotFoundException
     * @throws IOException
     */
    public File extractID3v2TagDataIntoFile(File outputFile) throws TagNotFoundException, IOException
    {
        int startByte = (int) ((MP3AudioHeader) audioHeader).getMp3StartByte();
        if (startByte >= 0)
        {

            //Read byte into buffer
            FileInputStream fis = new FileInputStream(file);
            FileChannel fc = fis.getChannel();
            ByteBuffer bb = ByteBuffer.allocate(startByte);
            fc.read(bb);

            //Write bytes to outputFile
            FileOutputStream out = new FileOutputStream(outputFile);
            out.write(bb.array());
            out.close();
            fc.close();
            fis.close();
            return outputFile;
        }
        throw new TagNotFoundException("There is no ID3v2Tag data in this file");
    }

    /**
     * Return audio header
     * @return
     */
    public MP3AudioHeader getMP3AudioHeader()
    {
        return (MP3AudioHeader) getAudioHeader();
    }

    /**
     * Creates a new MP3File datatype and parse the tag from the given file
     * Object.
     *
     * @param file MP3 file
     * @throws IOException  on any I/O error
     * @throws TagException on any exception generated by this library.
     * @throws org.jaudiotagger.audio.exceptions.ReadOnlyFileException
     * @throws org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
     */
    public MP3File(File file) throws IOException, TagException, ReadOnlyFileException, CannotReadException, InvalidAudioFrameException
    {
        this(file, LOAD_ALL);
    }

    /**
     * Calculates hash with given algorithm. Buffer size is 32768 byte.
     * Hash is calculated EXCLUDING meta-data, like id3v1 or id3v2
     *
     * @return hash value in byte
     * @param algorithm options MD5,SHA-1,SHA-256
     * @throws IOException 
     * @throws InvalidAudioFrameException 
     * @throws NoSuchAlgorithmException 
     */
    
    public byte[] getHash(String algorithm) throws NoSuchAlgorithmException, InvalidAudioFrameException, IOException{

			return getHash(algorithm, 32768);
		
		
    }
    
    /**
     * Calculates hash with given buffer size.
     * Hash is calculated EXCLUDING meta-data, like id3v1 or id3v2
     * @param  buffer
     * @return byte[] hash value in byte
     * @throws IOException 
     * @throws InvalidAudioFrameException 
     * @throws NoSuchAlgorithmException 
     */
    
    public byte[] getHash(int buffer) throws NoSuchAlgorithmException, InvalidAudioFrameException, IOException
    {
		return getHash("MD5", buffer);
    }
    /**
     * Calculates hash with algorithm "MD5". Buffer size is 32768 byte.
     * Hash is calculated EXCLUDING meta-data, like id3v1 or id3v2
     *
     * @return byte[] hash value.
     * @throws IOException 
     * @throws InvalidAudioFrameException 
     * @throws NoSuchAlgorithmException 
     */
    
    public byte[] getHash() throws NoSuchAlgorithmException, InvalidAudioFrameException, IOException
    {
	    return getHash("MD5", 32768);
    }
    
    /**
     * Calculates hash with algorithm "MD5", "SHA-1" or SHA-256".
     * Hash is calculated EXCLUDING meta-data, like id3v1 or id3v2
     *
     * @return byte[] hash value in byte
     * @throws IOException 
     * @throws InvalidAudioFrameException 
     * @throws NoSuchAlgorithmException 
     */
    
    public byte[] getHash(String algorithm, int bufferSize) throws InvalidAudioFrameException, IOException, NoSuchAlgorithmException
    {
    	File mp3File = getFile();
    	long startByte = getMP3StartByte(mp3File);
    	
    	int id3v1TagSize = 0;
		if (hasID3v1Tag()){
		ID3v1Tag id1tag= getID3v1Tag();
		id3v1TagSize  = id1tag.getSize();
		}
		
		InputStream inStream = new FileInputStream(mp3File);
		
		byte[] buffer = new byte[bufferSize];

		MessageDigest digest = MessageDigest.getInstance(algorithm);

		inStream.skip(startByte);
		
		int read;
		long totalSize = mp3File.length() - startByte - id3v1TagSize;
		int pointer  = buffer.length;
		
		while (pointer <= totalSize ) {
			
			read = inStream.read(buffer);
			
			digest.update(buffer, 0, read);
			pointer += buffer.length;
			}
		read = inStream.read(buffer,0,(int)totalSize - pointer + buffer.length);
		digest.update(buffer, 0, read);
		
		byte[] hash = digest.digest();

		inStream.close();
        return hash;
    }

    /**
     * Remove tag from file
     *
     * @param mp3tag
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void delete(AbstractTag mp3tag) throws FileNotFoundException, IOException
    {
        RandomAccessFile raf = new RandomAccessFile(this.file, "rw");
        mp3tag.delete(raf);
        raf.close();
        if(mp3tag instanceof ID3v1Tag)
        {
            id3v1tag=null;
        }

        if(mp3tag instanceof AbstractID3v2Tag)
        {
            id3v2tag=null;
        }
    }

    /**
     * Check can write to file
     *
     * @param file
     * @throws IOException
     */
    public void precheck(File file) throws IOException
    {
        if (!file.exists())
        {
            logger.severe(ErrorMessage.GENERAL_WRITE_FAILED_BECAUSE_FILE_NOT_FOUND.getMsg(file.getName()));
            throw new IOException(ErrorMessage.GENERAL_WRITE_FAILED_BECAUSE_FILE_NOT_FOUND.getMsg(file.getName()));
        }

        if (TagOptionSingleton.getInstance().isCheckIsWritable() && !file.canWrite())
        {
            logger.severe(ErrorMessage.GENERAL_WRITE_FAILED.getMsg(file.getName()));
            throw new IOException(ErrorMessage.GENERAL_WRITE_FAILED.getMsg(file.getName()));
        }

        if (file.length() <= MINIMUM_FILESIZE)
        {
            logger.severe(ErrorMessage.GENERAL_WRITE_FAILED_BECAUSE_FILE_IS_TOO_SMALL.getMsg(file.getName()));
            throw new IOException(ErrorMessage.GENERAL_WRITE_FAILED_BECAUSE_FILE_IS_TOO_SMALL.getMsg(file.getName()));
        }
    }

    /**
     * Saves the tags in this dataType to the file argument. It will be saved as
     * TagConstants.MP3_FILE_SAVE_WRITE
     *
     * @param fileToSave file to save the this dataTypes tags to
     * @throws FileNotFoundException if unable to find file
     * @throws IOException           on any I/O error
     */
    @Override
    public void save(File fileToSave) throws IOException
    {
        logger.config("Saving  : " + fileToSave.getPath());
        precheck(fileToSave);
        super.save(fileToSave);
    }

    @NonNull
    @Override
    protected AudioHeader readAudioHeader(@NonNull RandomAccessFile file, long id3v2size) throws CannotReadException, IOException
    {
        try
        {
            MP3AudioHeader header = new MP3AudioHeader(getFile(), id3v2size);
            //If the audio header is not straight after the end of the tag then search from start of file
            if (id3v2size != header.getMp3StartByte())
            {
                // AI: probable, the file is MP4 with audio stream encoded by MP3 codec. Checking it.
                file.seek(0);
                file.readInt(); // atom size
                if (file.readInt() == 0x66747970) // "ftyp" atom
                    throw new CannotReadException("The file is not valid MP3!");

                logger.config("First header found after tag:" + header);
                header = checkAudioStart(id3v2size, header);
            }
            return header;
        }
        catch (InvalidAudioFrameException e)
        {
            throw new CannotReadException(e);
        }
    }

    /**
     * Displays MP3File Structure
     */
    public String displayStructureAsXML()
    {
        createXMLStructureFormatter();
        tagFormatter.openHeadingElement("file", this.getFile().getAbsolutePath());
        if (this.getID3v1Tag() != null)
        {
            this.getID3v1Tag().createStructure();
        }
        if (this.getID3v2Tag() != null)
        {
            this.getID3v2Tag().createStructure();
        }
        tagFormatter.closeHeadingElement("file");
        return tagFormatter.toString();
    }

    /**
     * Displays MP3File Structure
     */
    public String displayStructureAsPlainText()
    {
        createPlainTextStructureFormatter();
        tagFormatter.openHeadingElement("file", this.getFile().getAbsolutePath());
        if (this.getID3v1Tag() != null)
        {
            this.getID3v1Tag().createStructure();
        }
        if (this.getID3v2Tag() != null)
        {
            this.getID3v2Tag().createStructure();
        }
        tagFormatter.closeHeadingElement("file");
        return tagFormatter.toString();
    }

    private static void createXMLStructureFormatter()
    {
        tagFormatter = new XMLTagDisplayFormatter();
    }

    private static void createPlainTextStructureFormatter()
    {
        tagFormatter = new PlainTextTagDisplayFormatter();
    }

    public static AbstractTagDisplayFormatter getStructureFormatter()
    {
        return tagFormatter;
    }

    /**
     * Overridden to only consider ID3v2 Tag
     *
     * @return
     */
    @Override
    public Tag getTagOrCreateDefault()
    {
        Tag tag = getID3v2Tag();
        if(tag==null)
            return createDefaultTag();
        return tag;
    }

    /**
     * Get the ID3v2 tag and convert to preferred version or if the file doesn't have one at all
     * create a default tag of preferred version and set it. The file may already contain a ID3v1 tag but because
     * this is not terribly useful the v1tag is not considered for this problem.
     *
     * @return
     */
    @Override
    public Tag getTagAndConvertOrCreateDefault()
    {
        Tag tag          = getTagOrCreateDefault();
        Tag convertedTag = convertID3Tag((AbstractID3v2Tag)tag, TagOptionSingleton.getInstance().getID3V2Version());
        if(convertedTag!=null)
        {
            return convertedTag;
        }
        return tag;
    }

    /**
     * Get the ID3v2 tag and convert to preferred version and set as the current tag
     *
     * @return
     */
    @Override
    public Tag getTagAndConvertOrCreateAndSetDefault()
    {
        Tag tag = getTagAndConvertOrCreateDefault();
        setTag(tag);
        return getTag();
    }
}

