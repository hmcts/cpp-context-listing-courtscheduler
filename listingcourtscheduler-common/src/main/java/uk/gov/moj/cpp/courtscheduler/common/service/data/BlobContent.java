package uk.gov.moj.cpp.courtscheduler.common.service.data;

import java.util.Arrays;
import java.util.Objects;

public class BlobContent {

    byte[] blobByteArray;

    public BlobContent(byte[] blobByteArray) {
        this.blobByteArray = blobByteArray;
    }

    public byte[] getBlobByteArray() {
        return blobByteArray;
    }

    public void setBlobByteArray(final byte[] blobByteArray) {
        this.blobByteArray = blobByteArray;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final BlobContent that = (BlobContent) o;
        return Objects.deepEquals(blobByteArray, that.blobByteArray);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(blobByteArray);
    }
}
