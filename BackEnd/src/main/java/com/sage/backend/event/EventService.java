package com.sage.backend.event;

import com.sage.backend.mapper.EventLogMapper;
import com.sage.backend.model.EventLog;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {

    private final EventLogMapper eventLogMapper;

    public EventService(EventLogMapper eventLogMapper) {
        this.eventLogMapper = eventLogMapper;
    }

    public void appendEvent(
            String taskId,
            String eventType,
            String fromState,
            String toState,
            int stateVersion,
            String payloadJson
    ) {
        EventLog eventLog = new EventLog();
        eventLog.setTaskId(taskId);
        eventLog.setEventType(eventType);
        eventLog.setFromState(fromState);
        eventLog.setToState(toState);
        eventLog.setStateVersion(stateVersion);
        eventLog.setEventPayloadJson(payloadJson);
        eventLogMapper.insert(eventLog);
    }

    public List<EventLog> findByTaskId(String taskId) {
        return eventLogMapper.findByTaskId(taskId);
    }
}

