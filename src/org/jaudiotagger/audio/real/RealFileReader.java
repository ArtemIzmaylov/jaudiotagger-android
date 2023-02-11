package org.jaudiotagger.audio.real;

import org.jaudiotagger.audio.SupportedFileFormat;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.generic.AudioFileReader;
import org.jaudiotagger.audio.generic.GenericAudioHeader;
import org.jaudiotagger.audio.generic.Utils;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Real Media File Format: Major Chunks: .RMF PROP MDPR CONT DATA INDX
 */
public class RealFileReader extends AudioFileReader
{

    @SuppressWarnings("unused")
	@Override
    protected GenericAudioHeader getEncodingInfo(RandomAccessFile raf) throws CannotReadException, IOException
    {
        GenericAudioHeader info = new GenericAudioHeader();
        RealChunk prop = findPropChunk(raf);
        DataInputStream dis = prop.getDataInputStream();
        int objVersion = Utils.readUint16(dis);
        if (objVersion == 0)
        {
            long maxBitRate       = Utils.readUint32(dis) / 1000;
            long avgBitRate       = Utils.readUint32(dis) / 1000;
            long maxPacketSize    = Utils.readUint32(dis);
            long avgPacketSize    = Utils.readUint32(dis);
            long packetCnt        = Utils.readUint32(dis);
            int duration          = (int)Utils.readUint32(dis) / 1000;
            long preroll          = Utils.readUint32(dis);
            long indexOffset      = Utils.readUint32(dis);
            long dataOffset       = Utils.readUint32(dis);
            int numStreams        = Utils.readUint16(dis);
            int flags             = Utils.readUint16(dis);
            info.setBitRate((int) avgBitRate);
            info.setPreciseLength(duration);
            info.setVariableBitRate(maxBitRate != avgBitRate);
            info.setFormat(SupportedFileFormat.RA.getDisplayName());
        }
        return info;
    }

    private RealChunk findPropChunk(RandomAccessFile raf) throws IOException, CannotReadException
    {
    	@SuppressWarnings("unused") RealChunk rmf = RealChunk.readChunk(raf);
        RealChunk prop = RealChunk.readChunk(raf);
        return prop;
    }

    private RealChunk findContChunk(RandomAccessFile raf) throws IOException, CannotReadException
    {
    	@SuppressWarnings("unused") RealChunk rmf = RealChunk.readChunk(raf);
    	@SuppressWarnings("unused") RealChunk prop = RealChunk.readChunk(raf);
        RealChunk rv = RealChunk.readChunk(raf);
        while (!rv.isCONT()) rv = RealChunk.readChunk(raf);
        return rv;
    }

    @Override
    protected Tag getTag(RandomAccessFile raf) throws CannotReadException, IOException
    {
        RealChunk cont = findContChunk(raf);
        DataInputStream dis = cont.getDataInputStream();
        String title = Utils.readString(dis, Utils.readUint16(dis));
        String author = Utils.readString(dis, Utils.readUint16(dis));
        String copyright = Utils.readString(dis, Utils.readUint16(dis));
        String comment = Utils.readString(dis, Utils.readUint16(dis));
        RealTag rv = new RealTag();
        // NOTE: frequently these fields are off-by-one, thus the crazy
        // logic below...
        try
        {
            rv.addField(FieldKey.TITLE,(title.isEmpty() ? author : title));
            rv.addField(FieldKey.ARTIST, title.isEmpty() ? copyright : author);
            rv.addField(FieldKey.COMMENT,comment);
        }
        catch(FieldDataInvalidException fdie)
        {
            throw new RuntimeException(fdie);
        }
        return rv;
    }

}

