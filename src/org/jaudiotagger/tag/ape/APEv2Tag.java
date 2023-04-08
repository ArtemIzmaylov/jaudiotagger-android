package org.jaudiotagger.tag.ape;

import androidx.annotation.NonNull;

import org.jaudiotagger.StandardCharsets;
import org.jaudiotagger.audio.ape.ApeFile;
import org.jaudiotagger.audio.generic.AbstractTag;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagNotFoundException;
import org.jaudiotagger.tag.id3.ID3v1Tag;
import org.jaudiotagger.tag.id3.valuepair.ImageFormats;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.jaudiotagger.tag.reference.PictureTypes;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * This class represents a APEv2 tag
 * <br>
 * @author Artem Izmaylov (www.aimp.ru)
 **/
public class APEv2Tag extends AbstractTag
{
    private static final Logger logger = Logger.getLogger("org.jaudiotagger.tag.ape");

    private static final long SIGNATURE = 0x4150455441474558L; // 'APETAGEX'
    private static final int TAG_FIELD_FLAG_READ_ONLY               = 1;
    private static final int TAG_FIELD_FLAG_DATA_TYPE_MASK          = 6;
    private static final int TAG_FIELD_FLAG_DATA_TYPE_TEXT_UTF8     = 0;
    private static final int TAG_FIELD_FLAG_DATA_TYPE_BINARY        = 1 << 1;
    private static final int TAG_FIELD_FLAG_DATA_TYPE_EXTERNAL_INFO = 2 << 1;
    private static final int TAG_FIELD_FLAG_DATA_TYPE_RESERVED      = 3 << 1;

    private static final int FOOTER_SIZE = 32;

    private static final EnumMap<FieldKey, String> tagFieldMap = new EnumMap<>(FieldKey.class);

    static
    {
        tagFieldMap.put(FieldKey.ACOUSTID_FINGERPRINT, "ACOUSTID_FINGERPRINT");
        tagFieldMap.put(FieldKey.ACOUSTID_ID, "ACOUSTID_ID");
        tagFieldMap.put(FieldKey.ALBUM, "ALBUM");
        tagFieldMap.put(FieldKey.ALBUM_ARTIST, "ALBUM ARTIST");
        tagFieldMap.put(FieldKey.ALBUM_YEAR, "ALBUM_YEAR");
        tagFieldMap.put(FieldKey.ALBUM_ARTISTS, "ALBUMARTISTS");
        tagFieldMap.put(FieldKey.ALBUM_ARTISTS_SORT, "ALBUMARTISTSSORT");
        tagFieldMap.put(FieldKey.ALBUM_ARTIST_SORT, "ALBUMARTISTSORT");
        tagFieldMap.put(FieldKey.ALBUM_SORT, "ALBUMSORT");
        tagFieldMap.put(FieldKey.AMAZON_ID, "ASIN");
        tagFieldMap.put(FieldKey.ARRANGER, "ARRANGER");
        tagFieldMap.put(FieldKey.ARRANGER_SORT, "ARRANGER_SORT");
        tagFieldMap.put(FieldKey.ARTIST, "ARTIST");
        tagFieldMap.put(FieldKey.ARTISTS, "ARTISTS");
        tagFieldMap.put(FieldKey.ARTISTS_SORT, "ARTISTS_SORT");
        tagFieldMap.put(FieldKey.ARTIST_SORT, "ARTISTSORT");
        tagFieldMap.put(FieldKey.BARCODE, "BARCODE");
        tagFieldMap.put(FieldKey.BPM, "BPM");
        tagFieldMap.put(FieldKey.CATALOG_NO, "LABELNO");
        tagFieldMap.put(FieldKey.CHOIR, "CHOIR");
        tagFieldMap.put(FieldKey.CHOIR_SORT, "CHOIR_SORT");
        tagFieldMap.put(FieldKey.CLASSICAL_CATALOG, "CLASSICAL_CATALOG");
        tagFieldMap.put(FieldKey.CLASSICAL_NICKNAME, "CLASSICAL_NICKNAME");
        tagFieldMap.put(FieldKey.COMMENT, "COMMENT");
        tagFieldMap.put(FieldKey.COMPOSER, "COMPOSER");
        tagFieldMap.put(FieldKey.COMPOSER_SORT, "COMPOSERSORT");
        tagFieldMap.put(FieldKey.COPYRIGHT, "COPYRIGHT");
        tagFieldMap.put(FieldKey.CONDUCTOR, "CONDUCTOR");
        tagFieldMap.put(FieldKey.CONDUCTOR_SORT, "CONDUCTOR_SORT");
        tagFieldMap.put(FieldKey.COUNTRY, "COUNTRY");
        tagFieldMap.put(FieldKey.COVER_ART, "COVER ART (FRONT)");
        tagFieldMap.put(FieldKey.CUESHEET, "CUESHEET");
        tagFieldMap.put(FieldKey.CUSTOM1, "CUSTOM1");
        tagFieldMap.put(FieldKey.CUSTOM2, "CUSTOM2");
        tagFieldMap.put(FieldKey.CUSTOM3, "CUSTOM3");
        tagFieldMap.put(FieldKey.CUSTOM4, "CUSTOM4");
        tagFieldMap.put(FieldKey.CUSTOM5, "CUSTOM5");
        tagFieldMap.put(FieldKey.DISC_NO, "DISCNUMBER");
        tagFieldMap.put(FieldKey.DISC_SUBTITLE, "DISCSUBTITLE");
        tagFieldMap.put(FieldKey.DISC_TOTAL, "DISCTOTAL");
        tagFieldMap.put(FieldKey.DJMIXER, "DJMIXER");
        tagFieldMap.put(FieldKey.DJMIXER_SORT, "DJMIXER_SORT");
        tagFieldMap.put(FieldKey.ENCODER, "VENDOR");     //Known as vendor in VorbisComment
        tagFieldMap.put(FieldKey.ENGINEER, "ENGINEER");
        tagFieldMap.put(FieldKey.ENGINEER_SORT, "ENGINEER_SORT");
        tagFieldMap.put(FieldKey.ENSEMBLE, "ENSEMBLE");
        tagFieldMap.put(FieldKey.ENSEMBLE_SORT, "ENSEMBLE_SORT");
        tagFieldMap.put(FieldKey.FBPM, "FBPM");
        tagFieldMap.put(FieldKey.GENRE, "GENRE");
        tagFieldMap.put(FieldKey.GROUP, "GROUP");
        tagFieldMap.put(FieldKey.GROUPING, "GROUPING");
        tagFieldMap.put(FieldKey.INSTRUMENT, "INSTRUMENT");
        tagFieldMap.put(FieldKey.INVOLVEDPEOPLE, "INVOLVEDPEOPLE");
        tagFieldMap.put(FieldKey.IPI, "IPI");
        tagFieldMap.put(FieldKey.ISRC, "ISRC");
        tagFieldMap.put(FieldKey.ISWC, "ISWC");
        tagFieldMap.put(FieldKey.IS_CLASSICAL, "IS_CLASSICAL");
        tagFieldMap.put(FieldKey.IS_COMPILATION, "COMPILATION");
        tagFieldMap.put(FieldKey.IS_GREATEST_HITS, "IS_GREATEST_HITS");
        tagFieldMap.put(FieldKey.IS_HD, "IS_HD");
        tagFieldMap.put(FieldKey.IS_LIVE, "IS_LIVE");
        tagFieldMap.put(FieldKey.IS_SOUNDTRACK, "IS_SOUNDTRACK");
        tagFieldMap.put(FieldKey.JAIKOZ_ID, "JAIKOZ_ID");
        tagFieldMap.put(FieldKey.KEY, "KEY");
        tagFieldMap.put(FieldKey.LANGUAGE, "LANGUAGE");
        tagFieldMap.put(FieldKey.LYRICIST, "LYRICIST");
        tagFieldMap.put(FieldKey.LYRICIST_SORT, "LYRICIST_SORT");
        tagFieldMap.put(FieldKey.LYRICS, "LYRICS");
        tagFieldMap.put(FieldKey.MEDIA, "MEDIA");
        tagFieldMap.put(FieldKey.MIXER, "MIXER");
        tagFieldMap.put(FieldKey.MIXER_SORT, "MIXER_SORT");
        tagFieldMap.put(FieldKey.MOOD, "MOOD");
        tagFieldMap.put(FieldKey.MOOD_ACOUSTIC, "MOOD_ACOUSTIC");
        tagFieldMap.put(FieldKey.MOOD_AGGRESSIVE, "MOOD_AGGRESSIVE");
        tagFieldMap.put(FieldKey.MOOD_AROUSAL, "MOOD_AROUSAL");
        tagFieldMap.put(FieldKey.MOOD_DANCEABILITY, "MOOD_DANCEABILITY");
        tagFieldMap.put(FieldKey.MOOD_ELECTRONIC, "MOOD_ELECTRONIC");
        tagFieldMap.put(FieldKey.MOOD_HAPPY, "MOOD_HAPPY");
        tagFieldMap.put(FieldKey.MOOD_INSTRUMENTAL, "MOOD_INSTRUMENTAL");
        tagFieldMap.put(FieldKey.MOOD_PARTY, "MOOD_PARTY");
        tagFieldMap.put(FieldKey.MOOD_RELAXED, "MOOD_RELAXED");
        tagFieldMap.put(FieldKey.MOOD_SAD, "MOOD_SAD");
        tagFieldMap.put(FieldKey.MOOD_VALENCE, "MOOD_VALENCE");
        tagFieldMap.put(FieldKey.MOVEMENT, "MOVEMENT");
        tagFieldMap.put(FieldKey.MOVEMENT_NO, "MOVEMENT_NO");
        tagFieldMap.put(FieldKey.MOVEMENT_TOTAL, "MOVEMENT_TOTAL");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_ARTISTID, "MUSICBRAINZ_ARTISTID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_DISC_ID, "MUSICBRAINZ_DISCID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_ORIGINAL_RELEASE_ID, "MUSICBRAINZ_ORIGINAL_ALBUMID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_RELEASEARTISTID, "MUSICBRAINZ_ALBUMARTISTID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_RELEASEID, "MUSICBRAINZ_ALBUMID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_RELEASE_COUNTRY, "RELEASECOUNTRY");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID, "MUSICBRAINZ_RELEASEGROUPID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_RELEASE_STATUS, "MUSICBRAINZ_ALBUMSTATUS");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_RELEASE_TRACK_ID, "MUSICBRAINZ_RELEASETRACKID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_RELEASE_TYPE, "MUSICBRAINZ_ALBUMTYPE");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_TRACK_ID, "MUSICBRAINZ_TRACKID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK, "MUSICBRAINZ_WORK");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_RECORDING_WORK, "MUSICBRAINZ_RECORDING_WORK");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_RECORDING_WORK_ID, "MUSICBRAINZ_RECORDING_WORK_ID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_ID, "MUSICBRAINZ_WORKID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL1, "MUSICBRAINZ_WORK_PART_LEVEL1");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL1_ID, "MUSICBRAINZ_WORK_PART_LEVEL1_ID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL1_TYPE, "MUSICBRAINZ_WORK_PART_LEVEL1_TYPE");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL2, "MUSICBRAINZ_WORK_PART_LEVEL2");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL2_ID, "MUSICBRAINZ_WORK_PART_LEVEL2_ID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL2_TYPE, "MUSICBRAINZ_WORK_PART_LEVEL2_TYPE");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL3, "MUSICBRAINZ_WORK_PART_LEVEL3");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL3_ID, "MUSICBRAINZ_WORK_PART_LEVEL3_ID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL3_TYPE, "MUSICBRAINZ_WORK_PART_LEVEL3_TYPE");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL4, "MUSICBRAINZ_WORK_PART_LEVEL4");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL4_ID, "MUSICBRAINZ_WORK_PART_LEVEL4_ID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL4_TYPE, "MUSICBRAINZ_WORK_PART_LEVEL4_TYPE");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL5, "MUSICBRAINZ_WORK_PART_LEVEL5");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL5_ID, "MUSICBRAINZ_WORK_PART_LEVEL5_ID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL5_TYPE, "MUSICBRAINZ_WORK_PART_LEVEL5_TYPE");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL6, "MUSICBRAINZ_WORK_PART_LEVEL6");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL6_ID, "MUSICBRAINZ_WORK_PART_LEVEL6_ID");
        tagFieldMap.put(FieldKey.MUSICBRAINZ_WORK_PART_LEVEL6_TYPE, "MUSICBRAINZ_WORK_PART_LEVEL6_TYPE");
        tagFieldMap.put(FieldKey.MUSICIP_ID, "MUSICIP_PUID");
        tagFieldMap.put(FieldKey.OCCASION, "OCCASION");
        tagFieldMap.put(FieldKey.OPUS, "OPUS");
        tagFieldMap.put(FieldKey.ORCHESTRA, "ORCHESTRA");
        tagFieldMap.put(FieldKey.ORCHESTRA_SORT, "ORCHESTRA_SORT");
        tagFieldMap.put(FieldKey.ORIGINAL_ALBUM, "ORIGINAL_ALBUM");
        tagFieldMap.put(FieldKey.ORIGINALRELEASEDATE, "ORIGINALRELEASEDATE");
        tagFieldMap.put(FieldKey.ORIGINAL_ARTIST, "ORIGINAL_ARTIST");
        tagFieldMap.put(FieldKey.ORIGINAL_LYRICIST, "ORIGINAL_LYRICIST");
        tagFieldMap.put(FieldKey.ORIGINAL_YEAR, "ORIGINAL_YEAR");
        tagFieldMap.put(FieldKey.OVERALL_WORK, "OVERALL_WORK");
        tagFieldMap.put(FieldKey.PART, "PART");
        tagFieldMap.put(FieldKey.PART_NUMBER, "PART_NUMBER");
        tagFieldMap.put(FieldKey.PART_TYPE, "PART_TYPE");
        tagFieldMap.put(FieldKey.PERFORMER, "PERFORMER");
        tagFieldMap.put(FieldKey.PERFORMER_NAME, "PERFORMER_NAME");
        tagFieldMap.put(FieldKey.PERFORMER_NAME_SORT, "PERFORMER_NAME_SORT");
        tagFieldMap.put(FieldKey.PERIOD, "PERIOD");
        tagFieldMap.put(FieldKey.PRODUCER, "PRODUCER");
        tagFieldMap.put(FieldKey.PRODUCER_SORT, "PRODUCER_SORT");
        tagFieldMap.put(FieldKey.QUALITY, "QUALITY");
        tagFieldMap.put(FieldKey.RANKING, "RANKING");
        tagFieldMap.put(FieldKey.RATING, "RATING");
        tagFieldMap.put(FieldKey.RECORD_LABEL, "LABEL");
        tagFieldMap.put(FieldKey.RECORDINGLOCATION, "RECORDINGLOCATION");
        tagFieldMap.put(FieldKey.RECORDINGDATE, "RECORDINGDATE");
        tagFieldMap.put(FieldKey.RECORDINGSTARTDATE, "RECORDINGSTARTDATE");
        tagFieldMap.put(FieldKey.RECORDINGENDDATE, "RECORDINGENDDATE");
        tagFieldMap.put(FieldKey.REMIXER, "REMIXER");
        tagFieldMap.put(FieldKey.ROONALBUMTAG, "ROONALBUMTAG");
        tagFieldMap.put(FieldKey.ROONTRACKTAG, "ROONTRACKTAG");
        tagFieldMap.put(FieldKey.SCRIPT, "SCRIPT");
        tagFieldMap.put(FieldKey.SECTION, "SECTION");
        tagFieldMap.put(FieldKey.SINGLE_DISC_TRACK_NO, "SINGLE_DISC_TRACK_NO");
        tagFieldMap.put(FieldKey.SONGKONG_ID, "SONGKONG_ID");
        tagFieldMap.put(FieldKey.SUBTITLE, "SUBTITLE");
        tagFieldMap.put(FieldKey.TAGS, "TAGS");
        tagFieldMap.put(FieldKey.TEMPO, "TEMPO");
        tagFieldMap.put(FieldKey.TIMBRE, "TIMBRE");
        tagFieldMap.put(FieldKey.TITLE, "TITLE");
        tagFieldMap.put(FieldKey.TITLE_MOVEMENT, "TITLE_MOVEMENT");
        tagFieldMap.put(FieldKey.TITLE_SORT, "TITLESORT");
        tagFieldMap.put(FieldKey.TONALITY, "TONALITY");
        tagFieldMap.put(FieldKey.TRACK, "TRACKNUMBER");
        tagFieldMap.put(FieldKey.TRACK_TOTAL, "TRACKTOTAL");
        tagFieldMap.put(FieldKey.URL_DISCOGS_ARTIST_SITE, "URL_DISCOGS_ARTIST_SITE");
        tagFieldMap.put(FieldKey.URL_DISCOGS_RELEASE_SITE, "URL_DISCOGS_RELEASE_SITE");
        tagFieldMap.put(FieldKey.URL_LYRICS_SITE, "URL_LYRICS_SITE");
        tagFieldMap.put(FieldKey.URL_OFFICIAL_ARTIST_SITE, "URL_OFFICIAL_ARTIST_SITE");
        tagFieldMap.put(FieldKey.URL_OFFICIAL_RELEASE_SITE, "URL_OFFICIAL_RELEASE_SITE");
        tagFieldMap.put(FieldKey.URL_WIKIPEDIA_ARTIST_SITE, "URL_WIKIPEDIA_ARTIST_SITE");
        tagFieldMap.put(FieldKey.URL_WIKIPEDIA_RELEASE_SITE, "URL_WIKIPEDIA_RELEASE_SITE");
        tagFieldMap.put(FieldKey.VERSION, "VERSION");
        tagFieldMap.put(FieldKey.WORK, "WORK");
        tagFieldMap.put(FieldKey.WORK_TYPE, "WORK_TYPE");
        tagFieldMap.put(FieldKey.YEAR, "YEAR");
    }
    
    public APEv2Tag()
    {
    }

    public APEv2Tag(@NonNull RandomAccessFile file) throws IOException, TagException
    {
        long id3v1size = getID3v1Size(file);
        long length = file.length();
        if (length <= id3v1size + FOOTER_SIZE)
            throw new TagNotFoundException("APEv2: no enough space for valid tag");

        // Check footer's signature
        file.seek(length - id3v1size - FOOTER_SIZE);
        long id = file.readLong();
        if (id != SIGNATURE)
            throw new TagNotFoundException("APEv2");

        // Read footer's data
        int version = ApeFile.readInt(file);
        int size = ApeFile.readInt(file);
        int fields = ApeFile.readInt(file);
        int flags = ApeFile.readInt(file);
        long reserved = file.readLong();

        // Check footer's data
        if (size < 0 || size + id3v1size > length)
            throw new TagException("APEv2: invalid tag size");
        if (fields < 0)
            throw new TagException("APEv2: invalid number of fields");

        // Read fields
        byte[] buffer = new byte[64];
        file.seek(length - id3v1size - size);
        for (; fields > 0; fields--)
        {
            int valueSize  = ApeFile.readInt(file);
            int valueFlags = ApeFile.readInt(file);
            if (valueSize > size)
                throw new TagException("APEv2: tag malformed");

            // read name
            int len = 0;
            while (true)
            {
                int chr = file.read();
                if (chr <= 0) break;
                if (len >= buffer.length)
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                buffer[len] = (byte)(chr & 0xFF);
                len++;
            }
            String fieldName = len > 0 ? new String(buffer, 0, len).toUpperCase() : "";

            // read value
            if (valueSize > buffer.length)
                buffer = new byte[valueSize];
            file.readFully(buffer, 0, valueSize);
            if ((valueFlags & TAG_FIELD_FLAG_DATA_TYPE_BINARY) != 0)
                addField(new APEv2TagFieldBinary(fieldName, Arrays.copyOf(buffer, valueSize)));
            else
                addField(new APEv2TagField(fieldName, new String(buffer, 0, valueSize, StandardCharsets.UTF_8)));
        }
    }

    public static void delete(@NonNull RandomAccessFile file) throws IOException
    {
        // TODO: keep ID3v1 tag data
        long id3v1Size = getID3v1Size(file);
        long length = file.length();
        file.seek(length - id3v1Size - FOOTER_SIZE);
        if (file.readLong() != SIGNATURE)
        {
            logger.config("Unable to find APEv2 tag to delete");
            return;
        }
        // Read footer's data
        int vers = ApeFile.readInt(file);
        int size = ApeFile.readInt(file);
        if (size < 0 || size > file.length())
            throw new IOException("APEv2: invalid tag size");
        file.setLength(length - size);
    }

    private static long getID3v1Size(@NonNull RandomAccessFile file) throws IOException
    {
        long length = file.length();
        if (length >= ID3v1Tag.TAG_LENGTH)
        {
            byte[] id = new byte[3];
            file.seek(length - ID3v1Tag.TAG_LENGTH);
            file.readFully(id, 0, id.length);
            if (Arrays.equals(id, ID3v1Tag.TAG_ID))
                return ID3v1Tag.TAG_LENGTH;
            // TODO: add ID3v1 Lyrics support
        }
        return 0;
    }

    public void write(@NonNull RandomAccessFile file) throws IOException
    {
        delete(file);

        long startPos = file.length();
        file.seek(startPos);

        List<TagField> fields = getAll();
        for (TagField field : fields)
        {
            byte[] data = field.getRawContent();
            ApeFile.writeInt(file, data.length);
            ApeFile.writeInt(file, field.isBinary() ? TAG_FIELD_FLAG_DATA_TYPE_BINARY : TAG_FIELD_FLAG_DATA_TYPE_TEXT_UTF8);
            file.write(field.getId().getBytes(StandardCharsets.UTF_8));
            file.write(0);
            file.write(data);
        }

        int size = (int)(file.length() - startPos + FOOTER_SIZE);
        file.writeLong(SIGNATURE);
        ApeFile.writeInt(file, 2000); // version
        ApeFile.writeInt(file, size);
        ApeFile.writeInt(file, fields.size());
        ApeFile.writeInt(file, 0); // flags;
        ApeFile.writeInt(file, 0); // reserved
        ApeFile.writeInt(file, 0); // reserved
    }

    @Override
    public TagField createField(Artwork artwork)
    {
        String fileName = "cover." + ImageFormats.getFormatForMimeType(artwork.getMimeType()).toLowerCase();
        byte[] picture = artwork.getBinaryData();
        byte[] picName = fileName.getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[picture.length + picName.length + 1];
        System.arraycopy(picName, 0, content, 0, picName.length);
        System.arraycopy(picture, 0, content, picName.length + 1, picture.length);
        return new APEv2TagFieldBinary(fieldKeyToNativeKey(FieldKey.COVER_ART), content);
    }

    @Override
    public TagField createField(FieldKey genericKey, String... value) throws KeyNotFoundException
    {
        if (genericKey == null)
            throw new KeyNotFoundException();
        return new APEv2TagField(tagFieldMap.get(genericKey), value[0]);
    }

    @Override
    public TagField createCompilationField(boolean value) throws KeyNotFoundException
    {
        return createField(FieldKey.IS_COMPILATION, String.valueOf(value));
    }

    @Override
    public void deleteField(FieldKey id) throws KeyNotFoundException
    {
        deleteField(fieldKeyToNativeKey(id));
    }

    @Override
    public TagField getFirstField(FieldKey genericKey) throws KeyNotFoundException
    {
        if (genericKey == null)
            throw new KeyNotFoundException();
        return getFirstField(tagFieldMap.get(genericKey));
    }

    @Override
    public List<Artwork> getArtworkList()
    {
        List<TagField> coverArts  = getFields(FieldKey.COVER_ART);
        List<Artwork> artworkList = new ArrayList<>(coverArts.size());
        for (TagField next : coverArts)
        {
            byte[] data = ((APEv2TagFieldBinary)next).getRawContent();
            if (data == null || data.length == 0) continue;

            // find and extract the filename
            int offset = 0;
            while (offset < data.length && data[offset] != 0)
                offset++;

            Artwork artwork = ArtworkFactory.getNew();
            artwork.setDescription(new String(data, 0, offset, StandardCharsets.UTF_8));
            artwork.setPictureType(PictureTypes.DEFAULT_ID);
            artwork.setBinaryData(Arrays.copyOfRange(data, offset + 1, data.length));
            artwork.setMimeType(ImageFormats.getMimeTypeForBinarySignature(artwork.getBinaryData()));
            artworkList.add(artwork);
        }
        return artworkList;
    }

    @Override
    protected boolean isAllowedEncoding(Charset enc)
    {
        return enc.equals(StandardCharsets.UTF_8);
    }

    @Override
    public List<String> getAll(FieldKey id) throws KeyNotFoundException
    {
        return super.getAll(fieldKeyToNativeKey(id));
    }

    @Override
    public List<TagField> getFields(FieldKey id) throws KeyNotFoundException
    {
        return super.getFields(fieldKeyToNativeKey(id));
    }

    @Override
    public String getValue(FieldKey id, int n)
    {
        return getItem(fieldKeyToNativeKey(id), n);
    }

    private String fieldKeyToNativeKey(FieldKey key) throws KeyNotFoundException
    {
        String nativeKey = tagFieldMap.get(key);
        if (nativeKey == null)
            throw new KeyNotFoundException();
        return nativeKey;
    }
}