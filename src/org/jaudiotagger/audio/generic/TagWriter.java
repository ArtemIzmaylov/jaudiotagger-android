package org.jaudiotagger.audio.generic;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.tag.Tag;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by Paul on 15/09/2015.
 */
public interface TagWriter
{
    void delete(Tag tag, RandomAccessFile raf, RandomAccessFile tempRaf) throws IOException;


    /**
     * Write tag to file
     *
     * @param tag
     * @param raf
     * @param rafTemp
     * @throws java.io.IOException
     */
    void write(AudioFile af, Tag tag, RandomAccessFile raf, RandomAccessFile rafTemp) throws IOException;
}
