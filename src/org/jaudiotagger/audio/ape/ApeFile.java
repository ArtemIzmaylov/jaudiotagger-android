package org.jaudiotagger.audio.ape;

import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.generic.AudioFileWithCommonTags;
import org.jaudiotagger.tag.TagException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * This class represents a physical APE File
 * <br>
 * @author Artem Izmaylov (www.aimp.ru)
 **/
public class ApeFile extends AudioFileWithCommonTags
{
    public ApeFile(File file, boolean readOnly) throws IOException, ReadOnlyFileException, CannotReadException, TagException
    {
        super(file, readOnly, LOAD_ALL);
    }

    @Override
    protected boolean isAPEv2priority()
    {
        return true;
    }

    @Override
    protected AudioHeader readAudioHeader(RandomAccessFile file, long id3v2size) throws CannotReadException, IOException
    {
        return new ApeAudioHeader(file, id3v2size);
    }

    public static int readInt(RandomAccessFile file) throws IOException
    {
        return Integer.reverseBytes(file.readInt());
    }

    public static int readUnsignedShort(RandomAccessFile file) throws IOException
    {
        return Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
    }

    public static void writeInt(RandomAccessFile file, int value) throws IOException
    {
        file.writeInt(Integer.reverseBytes(value));
    }
}