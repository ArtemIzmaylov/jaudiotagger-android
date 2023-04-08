package org.jaudiotagger.audio.generic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.logging.Hex;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagNotFoundException;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.ape.APEv2Tag;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v11Tag;
import org.jaudiotagger.tag.id3.ID3v1Tag;
import org.jaudiotagger.tag.id3.ID3v22Tag;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.reference.ID3V2Version;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * This is basic class for physical file formats that may contain ID3v1, ID3v2 or APEv2 tags
 * <br>
 * @author Artem Izmaylov (www.aimp.ru)
 **/
public abstract class AudioFileWithCommonTags extends AudioFile
{
    /* Load ID3v1 tag if exists */
    public static final int LOAD_IDV1TAG = 2;
    /* Load ID3v2 tag if exists */
    public static final int LOAD_IDV2TAG = 4;
    /* This option is currently ignored */
    public static final int LOAD_LYRICS3 = 8;
    /* Load APEv2 tag if exists */
    public static final int LOAD_APEv2TAG = 16;
    public static final int LOAD_ALL = LOAD_IDV1TAG | LOAD_IDV2TAG | LOAD_LYRICS3 | LOAD_APEv2TAG;

    @Nullable
    protected APEv2Tag apev2Tag;
    @Nullable
    protected ID3v1Tag id3v1tag;
    @Nullable
    protected AbstractID3v2Tag id3v2tag;

    public AudioFileWithCommonTags()
    {
    }

    public AudioFileWithCommonTags(File file, boolean readOnly, int options) throws IOException, ReadOnlyFileException, CannotReadException, TagException
    {
        this.file = file;
        try (RandomAccessFile raf = checkFilePermissions(file, readOnly))
        {
            // Read ID3v2 tag size (if tag exists) to allow audioHeader parsing to skip over tag
            long id3v2size = AbstractID3v2Tag.getV2TagSizeIfExists(file);
            logger.config("ID3v2.size:" + Hex.asHex(id3v2size));
            audioHeader = readAudioHeader(raf, id3v2size);

            if ((options & LOAD_IDV2TAG) != 0)
                readID3V2Tag(raf, (int) id3v2size);
            if ((options & LOAD_IDV1TAG) != 0)
                readID3V1Tag(raf);
            if ((options & LOAD_APEv2TAG) != 0)
                readAPEv2Tag(raf);

            if (apev2Tag != null && isAPEv2priority())
                tag = apev2Tag;
            else if (id3v2tag != null)
                tag = id3v2tag;
            else if (apev2Tag != null && !isAPEv2priority())
                tag = apev2Tag;
            else
                tag = id3v1tag;
        }
    }

    @Override
    public void commit() throws CannotWriteException
    {
        try
        {
            save();
        }
        catch (Exception e)
        {
            throw new CannotWriteException(e.getMessage());
        }
    }

    /**
     * Saves the tags in this dataType to the file referred to by this dataType.
     *
     * @throws IOException  on any I/O error
     */
    public void save() throws IOException
    {
        save(this.file);
    }

    /**
     * Saves the tags in this dataType to the file argument. It will be saved as
     * TagConstants.MP3_FILE_SAVE_WRITE
     *
     * @param fileToSave file to save the this dataTypes tags to
     * @throws FileNotFoundException if unable to find file
     * @throws IOException           on any I/O error
     */
    public void save(@NonNull File file) throws IOException
    {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw"))
        {
            // write or remove ID3v2
            if (TagOptionSingleton.getInstance().isId3v2Save())
            {
                if (id3v2tag != null)
                    id3v2tag.write(raf);
                else
                {
                    logger.config("Deleting ID3v2 tag:"+file.getName());
                    (new ID3v24Tag()).delete(raf);
                    (new ID3v23Tag()).delete(raf);
                    (new ID3v22Tag()).delete(raf);
                }
            }

            // write or remove APEv2
            if (apev2Tag != null)
                apev2Tag.write(raf);
            else
                APEv2Tag.delete(raf);

            if (TagOptionSingleton.getInstance().isId3v1Save())
            {
                // write ID3v1
                if (id3v1tag != null)
                    id3v1tag.write(raf);
                else
                    (new ID3v1Tag()).delete(raf);
            }
        }
    }

    @Override
    public Tag createDefaultTag()
    {
        if (isAPEv2priority())
            return new APEv2Tag();
        if (TagOptionSingleton.getInstance().getID3V2Version() == ID3V2Version.ID3_V24)
            return new ID3v24Tag();
        if (TagOptionSingleton.getInstance().getID3V2Version() == ID3V2Version.ID3_V23)
            return new ID3v23Tag();
        if (TagOptionSingleton.getInstance().getID3V2Version() == ID3V2Version.ID3_V22)
            return new ID3v22Tag();
        return new ID3v24Tag();
    }

    public boolean hasAPEv2Tag()
    {
        return apev2Tag != null;
    }

    public boolean hasID3v1Tag()
    {
        return id3v1tag != null;
    }

    public boolean hasID3v2Tag()
    {
        return id3v2tag != null;
    }

    @Nullable
    public APEv2Tag getAPEv2Tag()
    {
        return apev2Tag;
    }

    @Nullable
    public ID3v1Tag getID3v1Tag()
    {
        return id3v1tag;
    }

    @Nullable
    public AbstractID3v2Tag getID3v2Tag()
    {
        return id3v2tag;
    }

    public void setAPEv2Tag(@Nullable APEv2Tag tag)
    {
        logger.config("setting apev2: tag");
        apev2Tag = tag;
    }

    public void setID3v1Tag(@Nullable ID3v1Tag tag)
    {
        logger.config("setting tagv1:v1 tag");
        id3v1tag = tag;
    }

    public void setID3v2Tag(@Nullable AbstractID3v2Tag tag)
    {
        logger.config("setting tagv2:v2 tag");
        id3v2tag = tag;
    }

    @Override
    public void setTag(@Nullable Tag tag)
    {
        super.setTag(tag);
        if (tag instanceof ID3v1Tag)
            setID3v1Tag((ID3v1Tag)tag);
        if (tag instanceof AbstractID3v2Tag)
            setID3v2Tag((AbstractID3v2Tag)tag);
        if (tag instanceof APEv2Tag)
            setAPEv2Tag((APEv2Tag)tag);
    }

    protected boolean isAPEv2priority()
    {
        return false;
    }

    @NonNull
    protected abstract AudioHeader readAudioHeader(@NonNull RandomAccessFile file, long id3v2size) throws CannotReadException, IOException;

    private void readAPEv2Tag(@NonNull RandomAccessFile file) throws IOException
    {
        logger.finer("Attempting to read APEv2 tags");
        try
        {
            setAPEv2Tag(new APEv2Tag(file));
        }
        catch (TagNotFoundException e)
        {
            logger.config("No APEv2 tag found");
        }
        catch (TagException e)
        {
            throw new IOException(e);
        }
    }

    private void readID3V1Tag(@NonNull RandomAccessFile newFile) throws IOException
    {
        logger.finer("Attempting to read ID3v1 tags");
        try
        {
            setID3v1Tag(new ID3v11Tag(newFile, file.getName()));
        }
        catch (TagNotFoundException e1)
        {
            logger.config("No ID3v11 tag found");
            try
            {
                setID3v1Tag(new ID3v1Tag(newFile, file.getName()));
            }
            catch (TagNotFoundException e2)
            {
                logger.config("No ID3v1 tag found");
            }
        }
    }

    @SuppressWarnings("ChannelOpenedButNotSafelyClosed")
    private void readID3V2Tag(@NonNull RandomAccessFile newFile, int size) throws IOException, TagException
    {
        if (size < AbstractID3v2Tag.TAG_HEADER_LENGTH)
        {
            logger.config("Not enough space for valid ID3v2 tag:" + size);
            return;
        }

        logger.finer("Attempting to read ID3v2 tags");

        ByteBuffer bb = ByteBuffer.allocateDirect(size);
        newFile.seek(0);
        newFile.getChannel().read(bb, 0);
        bb.rewind();

        logger.config("Attempting to read ID3v2 tags");
        try
        {
            setID3v2Tag(new ID3v24Tag(bb, file.getName()));
        }
        catch (TagNotFoundException ex)
        {
            logger.config("No id3v24 tag found");
        }

        try
        {
            if (id3v2tag == null)
                setID3v2Tag(new ID3v23Tag(bb, file.getName()));
        }
        catch (TagNotFoundException ex)
        {
            logger.config("No id3v23 tag found");
        }

        try
        {
            if (id3v2tag == null)
                setID3v2Tag(new ID3v22Tag(bb, file.getName()));
        }
        catch (TagNotFoundException ex)
        {
            logger.config("No id3v22 tag found");
        }
    }
}