/*
 *  This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser
 *  General Public  License as published by the Free Software Foundation; either version 2.1 of the License,
 *  or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 *  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License along with this library; if not,
 *  you can get a copy from http://www.opensource.org/licenses/lgpl-license.php or write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
package org.jaudiotagger.tag.datatype;

import org.jaudiotagger.tag.InvalidDataTypeException;
import org.jaudiotagger.tag.id3.AbstractTagFrameBody;
import org.jaudiotagger.tag.id3.valuepair.EventTimingTypes;

/**
 * A single event timing code. Part of a list of timing codes ({@link EventTimingCodeList}), that are contained in
 * {@link org.jaudiotagger.tag.id3.framebody.FrameBodyETCO}.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 * @version $Id:$
 */
public class EventTimingCode extends AbstractDataType implements Cloneable
{

    private static final int SIZE = 5;
    private final NumberHashMap type = new NumberHashMap(DataTypes.OBJ_TYPE_OF_EVENT, null, 1);
    private final NumberFixedLength timestamp = new NumberFixedLength(DataTypes.OBJ_DATETIME, null, 4);

    public EventTimingCode(EventTimingCode copy) {
        super(copy);
        this.type.setValue(copy.type.getValue());
        this.timestamp.setValue(copy.timestamp.getValue());
    }

    public EventTimingCode(String identifier, AbstractTagFrameBody frameBody)
    {
        this(identifier, frameBody, 0x00, 0L);
    }

    public EventTimingCode(String identifier, AbstractTagFrameBody frameBody, int type, long timestamp)
    {
        super(identifier, frameBody);
        setBody(frameBody);
        this.type.setValue(type);
        this.timestamp.setValue(timestamp);
    }

    @Override
    public void setBody(AbstractTagFrameBody frameBody)
    {
        super.setBody(frameBody);
        this.type.setBody(frameBody);
        this.timestamp.setBody(frameBody);
    }

    public long getTimestamp()
    {
        return ((Number)timestamp.getValue()).longValue();
    }

    public void setTimestamp(long timestamp)
    {
        this.timestamp.setValue(timestamp);
    }

    public int getType()
    {
        return ((Number) type.getValue()).intValue();
    }

    public void setType(int type)
    {
        this.type.setValue(type);
    }

    @Override
    public int getSize()
    {
        return SIZE;
    }

    @Override
    public void readByteArray(byte[] buffer, int originalOffset) throws InvalidDataTypeException
    {
        int localOffset = originalOffset;
        int size = getSize();

        logger.finest("offset:" + localOffset);

        //The read has extended further than the defined frame size (ok to extend upto
        //size because the next datatype may be of length 0.)
        if (originalOffset > buffer.length-size)
        {
            logger.warning("Invalid size for FrameBody");
            throw new InvalidDataTypeException("Invalid size for FrameBody");
        }

        this.type.readByteArray(buffer, localOffset);
        localOffset += this.type.getSize();
        this.timestamp.readByteArray(buffer, localOffset);
    }

    @Override
    public byte[] writeByteArray()
    {
        byte[] typeData = this.type.writeByteArray();
        byte[] timeData = this.timestamp.writeByteArray();
        if (typeData == null || timeData == null) return null;

        byte[] objectData = new byte[typeData.length + timeData.length];
        System.arraycopy(typeData, 0, objectData, 0, typeData.length);
        System.arraycopy(timeData, 0, objectData, typeData.length, timeData.length);
        return objectData;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        EventTimingCode that = (EventTimingCode) o;
        return this.getType() == that.getType() && this.getTimestamp() == that.getTimestamp();
    }

    @Override
    public int hashCode()
    {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return getType() + " (\"" + EventTimingTypes.getInstanceOf().getValueForId(getType()) + "\"), " + getTimestamp();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return new EventTimingCode(this);
    }
}
