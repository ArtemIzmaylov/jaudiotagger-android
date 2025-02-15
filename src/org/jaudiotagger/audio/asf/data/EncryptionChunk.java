package org.jaudiotagger.audio.asf.data;

import org.jaudiotagger.audio.asf.util.Utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author eric
 */
public class EncryptionChunk extends Chunk
{
    private String keyID;

    private String licenseURL;
    private String protectionType;
    private String secretData;
    /**
     * The read strings.
     */
    private final ArrayList<String> strings;

    /**
     * Creates an instance.
     *
     * @param chunkLen Length of current chunk.
     */
    public EncryptionChunk(BigInteger chunkLen)
    {
        super(GUID.GUID_CONTENT_ENCRYPTION, chunkLen);
        this.strings = new ArrayList<>();
        this.secretData = "";
        this.protectionType = "";
        this.keyID = "";
        this.licenseURL = "";
    }

    /**
     * This method appends a String.
     *
     * @param toAdd String to add.
     */
    public void addString(String toAdd)
    {
        this.strings.add(toAdd);
    }

    /**
     * This method gets the keyID.
     *
     * @return
     */
    public String getKeyID()
    {
        return this.keyID;
    }

    /**
     * This method gets the license URL.
     *
     * @return
     */
    public String getLicenseURL()
    {
        return this.licenseURL;
    }

    /**
     * This method gets the secret data.
     *
     * @return
     */
    public String getProtectionType()
    {
        return this.protectionType;
    }

    /**
     * This method gets the secret data.
     *
     * @return
     */
    public String getSecretData()
    {
        return this.secretData;
    }

    /**
     * This method returns a collection of all {@link String}s which were addid
     * due {@link #addString(String)}.
     *
     * @return Inserted Strings.
     */
    public Collection<String> getStrings()
    {
        return new ArrayList<>(this.strings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String prettyPrint(String prefix)
    {
        StringBuilder result = new StringBuilder(super.prettyPrint(prefix));
        result.insert(0, Utils.LINE_SEPARATOR + prefix + " Encryption:" + Utils.LINE_SEPARATOR);
        result.append(prefix).append("	|->keyID ").append(this.keyID).append(Utils.LINE_SEPARATOR);
        result.append(prefix).append("	|->secretData ").append(this.secretData).append(Utils.LINE_SEPARATOR);
        result.append(prefix).append("	|->protectionType ").append(this.protectionType).append(Utils.LINE_SEPARATOR);
        result.append(prefix).append("	|->licenseURL ").append(this.licenseURL).append(Utils.LINE_SEPARATOR);
        this.strings.iterator();
        for (String string : this.strings)
        {
            result.append(prefix).append("   |->").append(string).append(Utils.LINE_SEPARATOR);
        }
        return result.toString();
    }

    /**
     * This method appends a String.
     *
     * @param toAdd String to add.
     */
    public void setKeyID(String toAdd)
    {
        this.keyID = toAdd;
    }

    /**
     * This method appends a String.
     *
     * @param toAdd String to add.
     */
    public void setLicenseURL(String toAdd)
    {
        this.licenseURL = toAdd;
    }

    /**
     * This method appends a String.
     *
     * @param toAdd String to add.
     */
    public void setProtectionType(String toAdd)
    {
        this.protectionType = toAdd;
    }

    /**
     * This method adds the secret data.
     *
     * @param toAdd String to add.
     */
    public void setSecretData(String toAdd)
    {
        this.secretData = toAdd;
    }
}
