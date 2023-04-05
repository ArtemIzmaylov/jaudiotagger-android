package org.jaudiotagger.audio.ape;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.generic.AudioFileReader;
import org.jaudiotagger.audio.generic.GenericAudioHeader;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class ApeFileReader extends AudioFileReader
{
    @Override
    public AudioFile read(File f) throws CannotReadException, IOException, TagException, ReadOnlyFileException, InvalidAudioFrameException
    {
        return new ApeFile(f);
    }

    @Override
    protected GenericAudioHeader getEncodingInfo(RandomAccessFile raf) throws CannotReadException, IOException
    {
        throw new RuntimeException("ApeFileReader.getEncodingInfo should be called");
    }

    @Override
    protected Tag getTag(RandomAccessFile raf) throws CannotReadException, IOException
    {
        throw new RuntimeException("ApeFileReader.getTag should be called");
    }
}
