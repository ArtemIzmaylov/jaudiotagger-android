package org.jaudiotagger.audio.ape;

import static org.jaudiotagger.audio.ape.ApeFile.readInt;
import static org.jaudiotagger.audio.ape.ApeFile.readUnsignedShort;

import android.annotation.SuppressLint;

import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.SupportedFileFormat;
import org.jaudiotagger.audio.exceptions.CannotReadException;

import java.io.IOException;
import java.io.RandomAccessFile;

public class ApeAudioHeader implements AudioHeader
{
    private static final int APE_SIGNATURE = 0x4D414320; // 'MAC '
    private static final int MAC_COMPRESSION_FAST = 1000;
    private static final int MAC_COMPRESSION_NORMAL = 2000;
    private static final int MAC_COMPRESSION_HIGH = 3000;
    private static final int MAC_COMPRESSION_EXTRA_HIGH = 4000;
    private static final int MAC_COMPRESSION_INSANE = 5000;
    private static final int MAC_FLAG_8_BIT = 1;
    private static final int MAC_FLAG_24_BIT = 8;

    private final long fAudioDataStartPosition;
    private final int bitsPerSample;
    private final int blocksPerFrame;
    private final int channels;
    private final int compressionLevel;
    private final int flags;
    private final int finalFrameBlocks;
    private final int sampleRate;
    private final int totalFrames;
    private final int version;

    public ApeAudioHeader(RandomAccessFile file, long id3v2size) throws IOException, CannotReadException
    {
        file.seek(id3v2size); // skip ID3v2

        fAudioDataStartPosition = file.getFilePointer();

        int signature = file.readInt();
        if (signature != APE_SIGNATURE)
            throw new CannotReadException("Monkey's Audio signature was not found");

        this.version = readUnsignedShort(file);
        if (version <= 0 || version > 32767)
            throw new CannotReadException("Monkey's Audio signature has invalid version: " + version);

        if (version >= 3980)
        {
            file.skipBytes(2); // padded
            int descriptorSize = readInt(file);
            int headerSize = readInt(file);
            file.seek(fAudioDataStartPosition + descriptorSize);

            this.compressionLevel = readUnsignedShort(file);
            this.flags = readUnsignedShort(file);
            this.blocksPerFrame = readInt(file);
            this.finalFrameBlocks = readInt(file);
            this.totalFrames = readInt(file);
            this.bitsPerSample = readUnsignedShort(file);
            this.channels = readUnsignedShort(file);
            this.sampleRate = readInt(file);
        }
        else
        {
            this.compressionLevel = readUnsignedShort(file);
            this.flags = readUnsignedShort(file);
            this.channels = readUnsignedShort(file);
            this.sampleRate = readInt(file);
            int headerBytes = readInt(file);
            int terminatingBytes = readInt(file);
            this.totalFrames = readInt(file);
            this.finalFrameBlocks = readInt(file);
            int reserved = readInt(file);

            if ((flags & MAC_FLAG_8_BIT) != 0)
                this.bitsPerSample = 8;
            else if ((flags & MAC_FLAG_24_BIT) != 0)
                this.bitsPerSample = 24;
            else
                this.bitsPerSample = 16;

            if (version >= 3950)
                this.blocksPerFrame = 73728 * 4;
            else if (version >= 3900)
                this.blocksPerFrame = 73728;
            else if (version >= 3800 && compressionLevel == MAC_COMPRESSION_EXTRA_HIGH)
                this.blocksPerFrame = 73728;
            else
                this.blocksPerFrame = 9216;
        }
    }

    @Override
    public Integer getByteRate()
    {
        return null;
    }

    @Override
    public String getBitRate()
    {
        return null;
    }

    @Override
    public long getBitRateAsNumber()
    {
        return 0;
    }

    @Override
    @SuppressLint("DefaultLocale")
    public String getEncodingType()
    {
        return String.format("Monkey's Audio v%.3f (%s compression)", (float)version / 1000f, getCompressionLevelAsString());
    }

    private String getCompressionLevelAsString()
    {
        if (compressionLevel == MAC_COMPRESSION_INSANE)
            return "Insane";
        if (compressionLevel == MAC_COMPRESSION_EXTRA_HIGH)
            return "Extra High";
        if (compressionLevel == MAC_COMPRESSION_HIGH)
            return "High";
        if (compressionLevel == MAC_COMPRESSION_FAST)
            return "Fast";
        if (compressionLevel == MAC_COMPRESSION_NORMAL)
            return "Normal";
        return "Unknown";
    }

    @Override
    public Long getAudioDataLength()
    {
        return null;
    }

    @Override
    public Long getAudioDataStartPosition()
    {
        return fAudioDataStartPosition;
    }

    @Override
    public Long getAudioDataEndPosition()
    {
        return null;
    }

    @Override
    public String getSampleRate()
    {
        return String.valueOf(sampleRate);
    }

    @Override
    public int getSampleRateAsNumber()
    {
        return sampleRate;
    }

    @Override
    public String getFormat()
    {
        return SupportedFileFormat.APE.getDisplayName();
    }

    @Override
    public String getChannels()
    {
        return String.valueOf(channels);
    }

    @Override
    public boolean isVariableBitRate()
    {
        return false;
    }

    @Override
    public int getTrackLength()
    {
        return (int)(getPreciseTrackLength() + 0.5);
    }

    @Override
    public double getPreciseTrackLength()
    {
        Long samples = getNoOfSamples();
        if (samples != null && sampleRate > 0)
            return (double)samples / (double)sampleRate;
        return 0;
    }

    @Override
    public int getBitsPerSample()
    {
        return bitsPerSample;
    }

    @Override
    public boolean isLossless()
    {
        return true;
    }

    @Override
    public Long getNoOfSamples()
    {
        if (totalFrames > 0)
            return (long)(totalFrames - 1) * ((long)blocksPerFrame) + (long)finalFrameBlocks;
        return null;
    }

    @Override
    public String toString()
    {
        return "APEAudioHeader{" +
                "bitsPerSample=" + bitsPerSample +
                ", blocksPerFrame=" + blocksPerFrame +
                ", channels=" + channels +
                ", compressionLevel=" + compressionLevel +
                ", flags=" + flags +
                ", finalFrameBlocks=" + finalFrameBlocks +
                ", sampleRate=" + sampleRate +
                ", totalFrames=" + totalFrames +
                ", version=" + version +
                '}';
    }
}