package uk.gov.moj.cpp.courtscheduler.common.service.data;

import java.util.Arrays;
import java.util.Objects;

public class BlobContent {

    /* package */
    byte[] blobByteArray;

    public BlobContent(final byte[] blobByteArray) {
        // Defensive copy: without this, the caller could mutate its own array after
        // construction and silently corrupt the content held by this object.
        this.blobByteArray = copyOrNull(blobByteArray);
    }

    public byte[] getBlobByteArray() {
        // Defensive copy: returning the live array would let callers mutate this
        // object's internal state from the outside.
        return copyOrNull(blobByteArray);
    }

    public void setBlobByteArray(final byte[] blobByteArray) {
        // Defensive copy: see constructor.
        this.blobByteArray = copyOrNull(blobByteArray);
    }

    private static byte[] copyOrNull(final byte[] array) {
        return array == null ? null : Arrays.copyOf(array, array.length);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BlobContent that = (BlobContent) o;
        return Objects.deepEquals(blobByteArray, that.blobByteArray);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(blobByteArray);
    }
}
