package org.jaudiotagger.audio.mp4.atom;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import org.jaudiotagger.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Ftyp (File Type) is the first atom, can be used to help identify the mp4 container type
 */
public class Mp4FtypBox extends AbstractMp4Box
{
    private String majorBrand;
    private int majorBrandVersion;
    private final List<String> compatibleBrands = new ArrayList<>();

    private static final int MAJOR_BRAND_LENGTH = 4;
    private static final int COMPATIBLE_BRAND_LENGTH = 4; //Can be multiple of these

    /**
     * @param header     header info
     * @param dataBuffer data of box (doesnt include header data)
     */
    public Mp4FtypBox(Mp4BoxHeader header, ByteBuffer dataBuffer)
    {
        this.header = header;
        this.dataBuffer = dataBuffer;
        this.dataBuffer.order(ByteOrder.BIG_ENDIAN);
    }

    public void processData()
    {
        CharsetDecoder decoder = StandardCharsets.ISO_8859_1.newDecoder();
        try
        {
            majorBrand = decoder.decode((ByteBuffer) dataBuffer.slice().limit(MAJOR_BRAND_LENGTH)).toString();
        }
        catch (CharacterCodingException cee)
        {
            //Ignore

        }
        dataBuffer.position(dataBuffer.position() + MAJOR_BRAND_LENGTH);
        majorBrandVersion = dataBuffer.getInt();
        while ((dataBuffer.position() < dataBuffer.limit()) && (dataBuffer.limit() - dataBuffer.position() >= COMPATIBLE_BRAND_LENGTH))
        {
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            try
            {
                String brand = decoder.decode((ByteBuffer) dataBuffer.slice().limit(COMPATIBLE_BRAND_LENGTH)).toString();
                //Sometimes just extra groups of four nulls
                if (!brand.equals("\u0000\u0000\u0000\u0000"))
                {
                    compatibleBrands.add(brand);
                }
            }
            catch (CharacterCodingException cee)
            {
                //Ignore    
            }
            dataBuffer.position(dataBuffer.position() + COMPATIBLE_BRAND_LENGTH);
        }
    }


    public String toString()
    {

        StringBuilder info = new StringBuilder("Major Brand:" + majorBrand + "Version:" + majorBrandVersion);
        if (!compatibleBrands.isEmpty())
        {
            info.append("Compatible:");
            for (String brand : compatibleBrands)
            {
                info.append(brand);
                info.append(",");
            }
            return info.substring(0, info.length() - 1);
        }
        return info.toString();
    }

    public String getMajorBrand()
    {
        return majorBrand;
    }


    public int getMajorBrandVersion()
    {
        return majorBrandVersion;
    }


    public List<String> getCompatibleBrands()
    {
        return compatibleBrands;
    }

    /**
     * Major brand, helps identify whats contained in the file, used by major and compatible brands
     * but this is not an exhaustive list, so for that reason we don't force the values read from the file
     * to tie in with this enum.
     */
    public enum Brand
    {
        ISO14496_1_BASE_MEDIA("isom", "ISO 14496-1"),
        ISO14496_12_BASE_MEDIA("iso2", "ISO 14496-12"),
        ISO14496_1_VERSION_1("mp41", "ISO 14496-1"),
        ISO14496_1_VERSION_2("mp42", "ISO 14496-2:Multi track with BIFS scenes"),
        QUICKTIME_MOVIE("qt  ", "Original Quicktime"),
        JVT_AVC("avc1", "JVT"),
        THREEG_MOBILE_MP4("MPA ", "3G Mobile"),
        APPLE_AAC_AUDIO("M4P ", "Apple Audio"),
        AES_ENCRYPTED_AUDIO("M4B ", "Apple encrypted Audio"),
        APPLE_AUDIO("mp71", "Apple Audio"),
        ISO14496_12_MPEG7_METADATA("mp71", "MAIN_SYNTHESIS"),
        APPLE_AUDIO_ONLY("M4A ", "M4A Audio"), //SOmetimes used by protected mutli track audio
        ;

        private final String id;
        private final String description;

        /**
         * @param id          it is stored as in file
         * @param description human readable description
         */
        Brand(String id, String description)
        {
            this.id = id;
            this.description = description;
        }

        public String getId()
        {
            return id;
        }

        public String getDescription()
        {
            return description;
        }

    }
}
