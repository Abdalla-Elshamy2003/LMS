package com.manarah.course;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.domain.LessonMaterial;
import com.manarah.course.repo.LessonMaterialRepository;
import com.manarah.common.storage.FileStorage;
import com.manarah.security.*;
import com.manarah.identity.domain.Role;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class VideoPlaybackPolicyTest {
    @Test void drmRequiredNeverFallsBackToDownloadableVideo() {
        var repo=mock(LessonMaterialRepository.class); var learning=mock(LearningService.class);
        var actor=new UserPrincipal(5L,1L,null,"Student","student@example.com",Role.STUDENT);
        var material=new LessonMaterial(); material.setId(3L); material.setTenantId(1L); material.setLessonId(2L); material.setType("VIDEO"); material.setFileKey("t1/materials/test.mp4");
        when(repo.findById(3L)).thenReturn(Optional.of(material)); when(learning.lessonCourse(actor,2L)).thenReturn(1L);
        var controller=new VideoPlaybackController(repo,learning,mock(FileStorage.class),mock(JwtService.class),mock(VideoWatchService.class),"",true,true);
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        assertThatThrownBy(()->controller.session(actor,3L,request)).isInstanceOf(ForbiddenException.class);
        material.setFileKey(null); material.setUrl("https://example.com/video.mp4");
        assertThatThrownBy(()->controller.session(actor,3L,request)).isInstanceOf(ForbiddenException.class);
        material.setUrl("https://player.vdocipher.com/v2/?video=01234567890123456789012345678901");
        assertThatThrownBy(()->controller.session(actor,3L,request)).isInstanceOf(BadRequestException.class);
    }
    @Test void playbackTicketIsBoundToIdentityTenantAndMaterialAndCannotAuthenticate() {
        var props=new JwtProperties(); props.setSecret("dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4="); var jwt=new JwtService(props);
        var owner=new UserPrincipal(5L,1L,null,"Student","s@example.com",Role.STUDENT);
        String ticket=jwt.playbackTicket(owner,3L);
        assertThatCode(()->jwt.verifyPlaybackTicket(ticket,owner,3L)).doesNotThrowAnyException();
        assertThatThrownBy(()->jwt.verifyPlaybackTicket(ticket,new UserPrincipal(6L,1L,null,"Other","o@example.com",Role.STUDENT),3L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(()->jwt.verifyPlaybackTicket(ticket,new UserPrincipal(5L,2L,null,"Other","o@example.com",Role.STUDENT),3L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(()->jwt.verifyPlaybackTicket(ticket,owner,4L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(()->jwt.parse(ticket)).isInstanceOf(Exception.class);
        var key=io.jsonwebtoken.security.Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(props.getSecret()));
        String expired=io.jsonwebtoken.Jwts.builder().subject("5").claim("purpose","video-playback").claim("tenant",1).claim("material",3).expiration(java.util.Date.from(java.time.Instant.now().minusSeconds(30))).signWith(key).compact();
        assertThatThrownBy(()->jwt.verifyPlaybackTicket(expired,owner,3L)).isInstanceOf(ForbiddenException.class);
    }
}
