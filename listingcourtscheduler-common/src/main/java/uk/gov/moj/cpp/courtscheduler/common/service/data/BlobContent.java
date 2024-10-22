package uk.gov.moj.cpp.courtscheduler.common.service.data;

import java.util.Arrays;
import java.util.Objects;

import com.microsoft.azure.storage.blob.CloudBlockBlob;

public class BlobContent {

    CloudBlockBlob blob;
    byte[] blobByteArray;

    public CloudBlockBlob getBlob() {
        return blob;
    }

    public void setBlob(final CloudBlockBlob blob) {
        this.blob = blob;
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
        return Objects.equals(getBlob(), that.getBlob()) && Arrays.equals(getBlobByteArray(), that.getBlobByteArray());
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(getBlob());
        result = 31 * result + Arrays.hashCode(getBlobByteArray());
        return result;
    }
}
