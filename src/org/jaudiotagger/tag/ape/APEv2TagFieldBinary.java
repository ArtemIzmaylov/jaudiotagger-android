package org.jaudiotagger.tag.ape;

import org.jaudiotagger.tag.TagField;

import java.io.UnsupportedEncodingException;

/**
 * This class represents the name and content of a binary tag entry in APE files.
 * <br>
 * @author Artem Izmaylov (www.aimp.ru)
 * */
public class APEv2TagFieldBinary implements TagField
{
    private final String id;
    private final byte[] content;

    APEv2TagFieldBinary(String id, byte[] content)
    {
        this.id = id;
        this.content = content;
    }

    @Override
    public void copyContent(TagField field)
    {
        throw new UnsupportedOperationException("Tag content cannot be copied");
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public byte[] getRawContent()
    {
        return content;
    }

    @Override
    public boolean isBinary()
    {
        return true;
    }

    @Override
    public void isBinary(boolean b)
    {
        if (!b)
            throw new UnsupportedOperationException("Tag cannot be converted to text");
    }

    @Override
    public boolean isCommon()
    {
        return false;
    }

    @Override
    public boolean isEmpty()
    {
        return content == null || content.length == 0;
    }
}
