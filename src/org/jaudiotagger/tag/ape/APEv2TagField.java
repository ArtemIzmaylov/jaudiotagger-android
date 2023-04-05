package org.jaudiotagger.tag.ape;

import androidx.annotation.NonNull;

import org.jaudiotagger.StandardCharsets;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagTextField;

import java.nio.charset.Charset;

/**
 * This class represents the name and content of a tag entry in APE files.
 * <br>
 * @author Artem Izmaylov (www.aimp.ru)
 * */
public class APEv2TagField implements TagTextField
{
    private final String id;
    private String content;

    APEv2TagField(String id, String content)
    {
        this.id = id;
        this.content = content;
    }

    @Override
    public String getContent()
    {
        return content;
    }

    @Override
    public Charset getEncoding()
    {
        return StandardCharsets.UTF_8;
    }

    @Override
    public void setContent(String content)
    {
        this.content = content;
    }

    @Override
    public void setEncoding(Charset encoding)
    {
        if (!StandardCharsets.UTF_8.equals(encoding))
            throw new UnsupportedOperationException("APE tag fields are always UTF8-encoded");
    }

    @Override
    public void copyContent(TagField field)
    {
        if (field instanceof TagTextField)
            setContent(((TagTextField)field).getContent());
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public byte[] getRawContent()
    {
        return content.getBytes(getEncoding());
    }

    @Override
    public boolean isBinary()
    {
        return false;
    }

    @Override
    public void isBinary(boolean b)
    {
        if (b)
            throw new UnsupportedOperationException("Tag cannot be converted to binary");
    }

    @Override
    public boolean isCommon()
    {
        return true;
    }

    @Override
    public boolean isEmpty()
    {
        return content == null || content.isEmpty();
    }

    @NonNull
    @Override
    public String toString()
    {
        return getContent();
    }
}
