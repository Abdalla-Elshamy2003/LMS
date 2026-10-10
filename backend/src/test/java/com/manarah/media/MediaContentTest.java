package com.manarah.media;

import com.manarah.common.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaContentTest {
    private final FileStorage storage = mock(FileStorage.class);
    private final byte[] bytes = new byte[]{1,2,3};

    @Test void objectUploadsKeepOnlyAReferenceAndCanBeReadBack() {
        var file = new MockMultipartFile("file", "receipt.png", "image/png", bytes);
        String key = "t3/receipts/one_receipt.png";
        when(storage.store(3L,"receipts",file)).thenReturn(key);
        when(storage.open(key)).thenReturn(new ByteArrayResource(bytes));
        var media = new MediaContent(storage,"s3");
        String stored = media.storeImage(3L,"receipts",file,"image/png",false);
        assertThat(stored).isEqualTo("object:image/png:"+key);
        assertThat(media.read(stored)).isEqualTo(bytes);
        assertThat(media.preview(stored)).isEqualTo("data:image/png;base64,AQID");
    }

    @Test void legacyImageAndReceiptBytesRemainReadableWithoutStorage() {
        var media = new MediaContent(storage,"s3");
        assertThat(media.read("AQID")).isEqualTo(bytes);
        assertThat(media.read("data:image/png;base64,AQID")).isEqualTo(bytes);
        assertThat(media.preview("data:image/png;base64,AQID")).isEqualTo("data:image/png;base64,AQID");
        verifyNoInteractions(storage);
    }

    @Test void objectReferencesCannotEscapeTheirTenantFolder() {
        var media = new MediaContent(storage,"s3");
        assertThatThrownBy(()->media.read("object:image/png:../../secret")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->media.read("object:text/html:t3/images/x.png")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(storage);
    }

    @Test void localDevelopmentRetainsTheExistingDatabaseFormat() {
        var file = new MockMultipartFile("file", "photo.png", "image/png", bytes);
        var media = new MediaContent(storage,"local");
        assertThat(media.storeImage(3L,"academy-images",file,"image/png",true))
                .isEqualTo("data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes));
        verifyNoInteractions(storage);
    }
}
