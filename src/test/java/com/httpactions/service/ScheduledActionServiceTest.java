package com.httpactions.service;

import com.httpactions.model.entity.Action;
import com.httpactions.model.entity.Installation;
import com.httpactions.model.enums.EventType;
import com.httpactions.model.enums.HttpMethod;
import com.httpactions.repository.ActionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledActionServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-30T12:00:00Z");

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private ExecutionService executionService;

    @Mock
    private InstallationService installationService;

    @Mock
    private TokenService tokenService;

    @Mock
    private RestClient restClient;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Spy
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks
    private ScheduledActionService scheduledActionService;

    // ---------------------------------------------------------------
    // evaluateScheduledActions — lock acquired
    // ---------------------------------------------------------------

    @Test
    void evaluateScheduledActions_acquiresLockAndProcesses() {
        when(jdbcTemplate.queryForObject(
                eq("SELECT pg_try_advisory_xact_lock(hashtext('scheduled-action-processor'))"),
                eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        // No scheduled actions — just verifying the lock path leads to query
        when(actionRepository.findDueScheduledActions(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        scheduledActionService.evaluateScheduledActions();

        verify(jdbcTemplate).queryForObject(
                eq("SELECT pg_try_advisory_xact_lock(hashtext('scheduled-action-processor'))"),
                eq(Boolean.class));
        verify(actionRepository).findDueScheduledActions(NOW.minusSeconds(60));
    }

    // ---------------------------------------------------------------
    // evaluateScheduledActions — lock not acquired
    // ---------------------------------------------------------------

    @Test
    void evaluateScheduledActions_skipsWhenLockNotAcquired() {
        when(jdbcTemplate.queryForObject(
                eq("SELECT pg_try_advisory_xact_lock(hashtext('scheduled-action-processor'))"),
                eq(Boolean.class)))
                .thenReturn(Boolean.FALSE);

        scheduledActionService.evaluateScheduledActions();

        verify(jdbcTemplate).queryForObject(anyString(), eq(Boolean.class));
        verifyNoInteractions(actionRepository);
        verifyNoInteractions(executionService);
    }

    @Test
    void evaluateScheduledActions_skipsWhenLockReturnsNull() {
        when(jdbcTemplate.queryForObject(
                eq("SELECT pg_try_advisory_xact_lock(hashtext('scheduled-action-processor'))"),
                eq(Boolean.class)))
                .thenReturn(null);

        scheduledActionService.evaluateScheduledActions();

        verifyNoInteractions(actionRepository);
        verifyNoInteractions(executionService);
    }

    // ---------------------------------------------------------------
    // processScheduledActions — fires due actions
    // ---------------------------------------------------------------

    @Test
    void evaluateScheduledActions_firesDueActions() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        // Action with cron "every minute" that last ran 2 minutes ago — should be due
        Action dueAction = createScheduledAction(
                "0 * * * * *",  // every minute (Spring 6-field cron)
                NOW.minusSeconds(120));
        dueAction.setEventType(EventType.NEW_PROJECT);

        when(actionRepository.findDueScheduledActions(any(Instant.class)))
                .thenReturn(List.of(dueAction));
        when(installationService.getActiveInstallation(dueAction.getWorkspaceId()))
                .thenReturn(java.util.Optional.of(activeInstallation()));

        scheduledActionService.evaluateScheduledActions();

        // Action should be saved (lastScheduledRun updated) and executed
        verify(actionRepository).save(dueAction);
        verify(executionService).processWebhookAsync(
                eq(dueAction.getWorkspaceId()),
                eq(dueAction.getEventType()),
                anyString());
    }

    // ---------------------------------------------------------------
    // processScheduledActions — skips non-due actions
    // ---------------------------------------------------------------

    @Test
    void evaluateScheduledActions_skipsNonDueActions() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        // Action with cron "every minute" that last ran just now — not due yet
        Action notDueAction = createScheduledAction(
                "0 * * * * *",  // every minute
                NOW);

        when(actionRepository.findDueScheduledActions(any(Instant.class)))
                .thenReturn(List.of(notDueAction));

        scheduledActionService.evaluateScheduledActions();

        verify(actionRepository, never()).save(any());
        verifyNoInteractions(executionService);
    }

    // ---------------------------------------------------------------
    // isDue — overdue action (tested indirectly through evaluateScheduledActions)
    // ---------------------------------------------------------------

    @Test
    void evaluateScheduledActions_neverRunAction_isDue() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        // Action that has never run (lastScheduledRun == null) — should be due
        Action neverRunAction = createScheduledAction("0 * * * * *", null);
        neverRunAction.setEventType(EventType.NEW_PROJECT);

        when(actionRepository.findDueScheduledActions(any(Instant.class)))
                .thenReturn(List.of(neverRunAction));
        when(installationService.getActiveInstallation(neverRunAction.getWorkspaceId()))
                .thenReturn(java.util.Optional.of(activeInstallation()));

        scheduledActionService.evaluateScheduledActions();

        verify(actionRepository).save(neverRunAction);
        verify(executionService).processWebhookAsync(
                eq(neverRunAction.getWorkspaceId()),
                eq(neverRunAction.getEventType()),
                anyString());
    }

    // ---------------------------------------------------------------
    // isDue — future action (not due)
    // ---------------------------------------------------------------

    @Test
    void evaluateScheduledActions_futureLastRun_notDue() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        // Action that ran 5 seconds ago with a daily cron — should not be due
        Action futureAction = createScheduledAction(
                "0 0 0 * * *",  // once a day at midnight
                NOW.minusSeconds(5));

        when(actionRepository.findDueScheduledActions(any(Instant.class)))
                .thenReturn(List.of(futureAction));

        scheduledActionService.evaluateScheduledActions();

        verify(actionRepository, never()).save(any());
        verifyNoInteractions(executionService);
    }

    // ---------------------------------------------------------------
    // processScheduledActions — invalid cron expression handled
    // ---------------------------------------------------------------

    @Test
    void evaluateScheduledActions_invalidCron_skipsGracefully() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        Action badCronAction = createScheduledAction("not-a-cron", null);

        when(actionRepository.findDueScheduledActions(any(Instant.class)))
                .thenReturn(List.of(badCronAction));

        // Should not throw
        scheduledActionService.evaluateScheduledActions();

        verify(actionRepository, never()).save(any());
        verifyNoInteractions(executionService);
    }

    @Test
    void evaluateScheduledActions_skipsExecutionWhenClockifyPayloadFetchFails() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        Action dueAction = createScheduledAction(
                "0 * * * * *",
                NOW.minusSeconds(120));

        when(actionRepository.findDueScheduledActions(any(Instant.class)))
                .thenReturn(List.of(dueAction));
        when(installationService.getActiveInstallation(dueAction.getWorkspaceId()))
                .thenReturn(java.util.Optional.empty());

        scheduledActionService.evaluateScheduledActions();

        verify(actionRepository, never()).save(any());
        verifyNoInteractions(executionService);
    }

    @Test
    void evaluateScheduledActions_skipsInactiveWorkspaceForNonTimeEntryEvents() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        Action dueAction = createScheduledAction(
                "0 * * * * *",
                NOW.minusSeconds(120));
        dueAction.setEventType(EventType.NEW_PROJECT);

        when(actionRepository.findDueScheduledActions(any(Instant.class)))
                .thenReturn(List.of(dueAction));
        when(installationService.getActiveInstallation(dueAction.getWorkspaceId()))
                .thenReturn(java.util.Optional.empty());

        scheduledActionService.evaluateScheduledActions();

        verify(actionRepository, never()).save(any());
        verifyNoInteractions(executionService);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Action createScheduledAction(String cronExpression, Instant lastScheduledRun) {
        Action a = new Action();
        a.setId(UUID.randomUUID());
        a.setWorkspaceId("ws-sched");
        a.setName("Scheduled Action");
        a.setEventType(EventType.NEW_TIME_ENTRY);
        a.setHttpMethod(HttpMethod.POST);
        a.setUrlTemplate("https://example.com/hook");
        a.setEnabled(true);
        a.setCronExpression(cronExpression);
        a.setLastScheduledRun(lastScheduledRun);
        a.setRetryCount(0);
        return a;
    }

    private Installation activeInstallation() {
        Installation installation = new Installation();
        installation.setWorkspaceId("ws-sched");
        installation.setStatus(com.httpactions.model.enums.InstallationStatus.ACTIVE);
        return installation;
    }
}
