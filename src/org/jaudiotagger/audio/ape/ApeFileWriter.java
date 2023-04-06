package org.jaudiotagger.audio.ape;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.generic.AudioFileWriter;
import org.jaudiotagger.tag.Tag;

import java.io.IOException;
import java.io.RandomAccessFile;

public class ApeFileWriter extends AudioFileWriter
{
    @Override
    public synchronized void delete(AudioFile af) throws CannotWriteException
    {
        ((ApeFile)af).setID3v1Tag(null);
        ((ApeFile)af).setID3v2Tag(null);
        ((ApeFile)af).setAPEv2Tag(null);
        af.commit();
    }

    @Override
    protected void deleteTag(Tag tag, RandomAccessFile raf, RandomAccessFile tempRaf) throws CannotReadException, CannotWriteException, IOException
    {
        throw new RuntimeException("ApeFileWriter.deleteTag should not be called");
    }

    @Override
    protected void writeTag(AudioFile audioFile, Tag tag, RandomAccessFile raf, RandomAccessFile rafTemp) throws CannotReadException, CannotWriteException, IOException
    {
        throw new RuntimeException("ApeFileWriter.writeTag should not be called");
    }
}
