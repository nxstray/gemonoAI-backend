package com.gemono.backend.functional;

import com.gemono.backend.model.*;
import com.gemono.backend.repository.*;
import com.gemono.backend.service.*;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Functional Tests — guest history merge behavior.
 * Covers all edge cases of the merge-on-login feature.
 *
 * Run: mvn test -Dgroups=functional
 */
@Epic("Functional")
@Feature("Guest History Merge")
@Tag("functional")
@ExtendWith(MockitoExtension.class)
@DisplayName("Functional Test — Guest History Merge")
class GuestMergeFunctionalTest {

    @Mock private GuestConversationRepository guestConvRepo;
    @Mock private GuestMessageRepository guestMsgRepo;
    @Mock private ConversationRepository conversationRepo;
    @Mock private MessageRepository messageRepo;
    @Mock private UserService userService;
    @Mock private AgentService agentService;
    @InjectMocks private GuestService guestService;

    private User testUser;

    @BeforeEach
    void setup() {
        testUser = User.builder()
                .email("user@example.com")
                .fullName("Test User")
                .role(User.Role.USER)
                .build();
    }

    // TC-FUNC-08
    @Test
    @Story("Merge")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-FUNC-08 | mergeGuestHistory returns 0 when no guest conversations exist")
    void TC_FUNC_08_mergeReturnsZeroWhenNoConversations() {
        when(guestConvRepo.findByGuestIdAndMergedToUserIsNull("empty-guest"))
                .thenReturn(List.of());

        int merged = guestService.mergeGuestHistory("empty-guest", "user@example.com");

        assertThat(merged).isEqualTo(0);
        verify(conversationRepo, never()).save(any());
    }

    // TC-FUNC-09
    @Test
    @Story("Merge")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-FUNC-09 | mergeGuestHistory marks all merged conversations as consumed")
    void TC_FUNC_09_mergeMarksConversationsAsConsumed() {
        GuestConversation guestConv = GuestConversation.builder()
                .guestId("guest-abc")
                .title("Test conversation")
                .build();

        when(userService.findByEmail("user@example.com")).thenReturn(testUser);
        when(guestConvRepo.findByGuestIdAndMergedToUserIsNull("guest-abc"))
                .thenReturn(List.of(guestConv));
        when(guestMsgRepo.findByGuestConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(conversationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        guestService.mergeGuestHistory("guest-abc", "user@example.com");

        // mergedToUser should be set
        assertThat(guestConv.getMergedToUser()).isEqualTo(testUser);
        assertThat(guestConv.getMergedAt()).isNotNull();
        verify(guestConvRepo).save(guestConv);
    }

    // TC-FUNC-10
    @Test
    @Story("Merge")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-FUNC-10 | mergeGuestHistory copies all messages to real conversation")
    void TC_FUNC_10_mergeCopiessAllMessages() {
        GuestConversation guestConv = GuestConversation.builder()
                .guestId("guest-msg-test")
                .title("Test with messages")
                .build();

        GuestMessage msg1 = GuestMessage.builder()
                .role("user").content("Hello").guestConversation(guestConv).build();
        GuestMessage msg2 = GuestMessage.builder()
                .role("assistant").content("Hi there!").guestConversation(guestConv).build();

        when(userService.findByEmail("user@example.com")).thenReturn(testUser);
        when(guestConvRepo.findByGuestIdAndMergedToUserIsNull("guest-msg-test"))
                .thenReturn(List.of(guestConv));
        when(guestMsgRepo.findByGuestConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(msg1, msg2));
        when(conversationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        guestService.mergeGuestHistory("guest-msg-test", "user@example.com");

        // Should save 2 messages to real conversation
        verify(messageRepo, times(2)).save(any(Message.class));
    }

    // TC-FUNC-11
    @Test
    @Story("Merge")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-FUNC-11 | mergeGuestHistory returns correct count of merged conversations")
    void TC_FUNC_11_mergeReturnsCorrectCount() {
        GuestConversation c1 = GuestConversation.builder()
                .guestId("guest-count").title("Conv 1").build();
        GuestConversation c2 = GuestConversation.builder()
                .guestId("guest-count").title("Conv 2").build();
        GuestConversation c3 = GuestConversation.builder()
                .guestId("guest-count").title("Conv 3").build();

        when(userService.findByEmail("user@example.com")).thenReturn(testUser);
        when(guestConvRepo.findByGuestIdAndMergedToUserIsNull("guest-count"))
                .thenReturn(List.of(c1, c2, c3));
        when(guestMsgRepo.findByGuestConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(conversationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int count = guestService.mergeGuestHistory("guest-count", "user@example.com");

        assertThat(count).isEqualTo(3);
    }
}