package org.jaudiotagger.audio.aiff.chunk;

import org.jaudiotagger.StandardCharsets;
import org.jaudiotagger.audio.aiff.AiffAudioHeader;
import org.jaudiotagger.audio.aiff.AiffUtil;
import org.jaudiotagger.audio.generic.Utils;
import org.jaudiotagger.audio.iff.Chunk;
import org.jaudiotagger.audio.iff.ChunkHeader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;

/**
 * <p>
 *     A comment consists of a time stamp, marker id, and a text count followed by text.
 * </p>
 * <pre>
 * typedef struct {
 *    unsigned long   timeStamp;
 *    MarkerID        marker;
 *    unsigned short  count;
 *    char            text[];
 * } Comment;
 * </pre>
 * <p>
 *     {@code timeStamp} indicates when the comment was created. Units are the number of seconds
 *     since January 1, 1904. (This time convention is the one used by the Macintosh. For procedures
 *     that manipulate the time stamp, see The Operating System Utilities chapter in Inside Macintosh,
 *     vol II). For a routine that will convert this to an Apple II GS/OS format time, please see
 *     Apple II File Type Note for filetype 0xD8, aux type 0x0000.
 * </p>
 * <p>
 *     A comment can be linked to a marker. This allows applications to store long descriptions of
 *     markers as a comment. If the comment is referring to a marker, then marker is the ID of that
 *     marker. Otherwise, marker is zero, indicating that this comment is not linked to a marker.
 * </p>
 * <p>
 *     {@code count} is the length of the text that makes up the comment. This is a 16 bit quantity,
 *     allowing much longer comments than would be available with a pstring.
 * </p>
 * <p>
 *     {@code text} contains the comment itself. This text must be padded with a byte at the end to
 *     insure that it is an even number of bytes in length. This pad byte, if present, is not
 *     included in count.
 * </p>
 *
 * @see AnnotationChunk
 */
public class CommentsChunk extends Chunk
{
    private final AiffAudioHeader aiffHeader;

    /**
     * @param chunkHeader The header for this chunk
     * @param chunkData The buffer from which the AIFF data are being read
     * @param aiffAudioHeader audio header
     */
    public CommentsChunk(ChunkHeader chunkHeader, ByteBuffer chunkData, AiffAudioHeader aiffAudioHeader)
    {
        super(chunkData, chunkHeader);
        this.aiffHeader = aiffAudioHeader;
    }

    /**
     * Reads a chunk and extracts information.
     *
     * @return <code>false</code> if the chunk is structurally
     * invalid, otherwise <code>true</code>
     */
    public boolean readChunk() throws IOException
    {
        int numComments = Utils.u(chunkData.getShort());

        //For each comment
        for (int i = 0; i < numComments; i++)
        {
            long timestamp  = Utils.u(chunkData.getInt());
            Date jTimestamp = AiffUtil.timestampToDate(timestamp);
            @SuppressWarnings("unused") int marker      = Utils.u(chunkData.getShort());
            int count       = Utils.u(chunkData.getShort());
            // Append a timestamp to the comment
            String text = Utils.getString(chunkData, 0, count, StandardCharsets.ISO_8859_1) + " " + AiffUtil.formatDate(jTimestamp);
            if (count % 2 != 0)
            {
                //#300
                //if count is odd, text is padded with an extra byte that we need to consume
                //but possibly the extra byte is missing so allow for that
                if(chunkData.position()< chunkData.limit())
                {
                    chunkData.get();
                }
            }
            aiffHeader.addComment(text);
        }
        return true;
    }

}
