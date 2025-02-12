package org.jaudiotagger.audio.asf.data;

import org.jaudiotagger.audio.asf.util.ChunkPositionComparator;
import org.jaudiotagger.audio.asf.util.Utils;

import java.math.BigInteger;
import java.util.*;

/**
 * Stores multiple ASF objects (chunks) in form of {@link Chunk} objects, and is
 * itself an ASF object (chunk).<br>
 * <br>
 * Because current implementation is solely used for ASF metadata, all chunks
 * (except for {@link StreamChunk}) may only be {@linkplain #addChunk(Chunk)
 * inserted} once.
 *
 * @author Christian Laireiter
 */
public class ChunkContainer extends Chunk
{

    /**
     * Stores the {@link GUID} instances, which are allowed multiple times
     * within an ASF header.
     */
    private final static Set<GUID> MULTI_CHUNKS;

    static
    {
        MULTI_CHUNKS = new HashSet<>();
        MULTI_CHUNKS.add(GUID.GUID_STREAM);
    }

    /**
     * Tests whether all stored chunks have a unique starting position among
     * their brothers.
     *
     * @param container the container to test.
     * @return <code>true</code> if all chunks are located at an unique
     * position. However, no intersection is tested.
     */
    protected static boolean chunkstartsUnique(ChunkContainer container)
    {
        boolean result = true;
        Set<Long> chunkStarts = new HashSet<>();
        Collection<Chunk> chunks = container.getChunks();
        for (Chunk curr : chunks)
        {
            result &= chunkStarts.add(curr.getPosition());
        }
        return result;
    }

    /**
     * Stores the {@link Chunk} objects to their {@link GUID}.
     */
    private final Map<GUID, List<Chunk>> chunkTable;

    /**
     * Creates an instance.
     *
     * @param chunkGUID the GUID which identifies the chunk.
     * @param pos       the position of the chunk within the stream.
     * @param length    the length of the chunk.
     */
    public ChunkContainer(GUID chunkGUID, long pos, BigInteger length)
    {
        super(chunkGUID, pos, length);
        this.chunkTable = new Hashtable<>();
    }

    /**
     * Adds a chunk to the container.<br>
     *
     * @param toAdd The chunk which is to be added.
     * @throws IllegalArgumentException If a chunk of same type is already added, except for
     *                                  {@link StreamChunk}.
     */
    public void addChunk(Chunk toAdd)
    {
        List<Chunk> list = assertChunkList(toAdd.getGuid());
        if (!list.isEmpty() && !MULTI_CHUNKS.contains(toAdd.getGuid()))
        {
            throw new IllegalArgumentException("The GUID of the given chunk indicates, that there is no more instance allowed."); //$NON-NLS-1$
        }
        list.add(toAdd);
        assert chunkstartsUnique(this) : "Chunk has equal start position like an already inserted one."; //$NON-NLS-1$
    }

    /**
     * This method asserts that a {@link List} exists for the given {@link GUID}
     * , in {@link #chunkTable}.<br>
     *
     * @param lookFor The GUID to get list for.
     * @return an already existing, or newly created list.
     */
    protected List<Chunk> assertChunkList(GUID lookFor)
    {
        List<Chunk> result = this.chunkTable.get(lookFor);
        if (result == null)
        {
            result = new ArrayList<>();
            this.chunkTable.put(lookFor, result);
        }
        return result;
    }

    /**
     * Returns a collection of all contained chunks.<br>
     *
     * @return all contained chunks
     */
    public Collection<Chunk> getChunks()
    {
        List<Chunk> result = new ArrayList<>();
        for (List<Chunk> curr : this.chunkTable.values())
        {
            result.addAll(curr);
        }
        return result;
    }

    /**
     * Looks for the first stored chunk which has the given GUID.
     *
     * @param lookFor    GUID to look up.
     * @param instanceOf The class which must additionally be matched.
     * @return <code>null</code> if no chunk was found, or the stored instance
     * doesn't match.
     */
    protected Chunk getFirst(GUID lookFor, Class<? extends Chunk> instanceOf)
    {
        Chunk result = null;
        List<Chunk> list = this.chunkTable.get(lookFor);
        if (list != null && !list.isEmpty())
        {
            Chunk chunk = list.get(0);
            if (instanceOf.isAssignableFrom(chunk.getClass()))
            {
                result = chunk;
            }
        }
        return result;
    }

    /**
     * This method checks if a chunk has been {@linkplain #addChunk(Chunk)
     * added} with specified {@linkplain Chunk#getGuid() GUID}.<br>
     *
     * @param lookFor GUID to look up.
     * @return <code>true</code> if chunk with specified GUID has been added.
     */
    public boolean hasChunkByGUID(GUID lookFor)
    {
        return this.chunkTable.containsKey(lookFor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String prettyPrint(String prefix)
    {
        return prettyPrint(prefix, "");
    }

    /**
     * Nearly the same as {@link #prettyPrint(String)} however, additional
     * information can be injected below the {@link Chunk#prettyPrint(String)}
     * output and the listing of the contained chunks.<br>
     *
     * @param prefix        The prefix to prepend.
     * @param containerInfo Information to inject.
     * @return Information of current Chunk Object.
     */
    public String prettyPrint(String prefix, String containerInfo)
    {
        StringBuilder result = new StringBuilder(super.prettyPrint(prefix));
        result.append(containerInfo);
        result.append(prefix).append("  |").append(Utils.LINE_SEPARATOR);
        ArrayList<Chunk> list = new ArrayList<>(getChunks());
        Collections.sort(list, new ChunkPositionComparator());

        for (Chunk curr : list)
        {
            result.append(curr.prettyPrint(prefix + "  |"));
            result.append(prefix).append("  |").append(Utils.LINE_SEPARATOR);
        }
        return result.toString();
    }
}
