package app.foxochat.handler.structure;

import app.foxochat.constant.CloseCodeConstant;
import app.foxochat.constant.ExceptionConstant;
import app.foxochat.constant.GatewayConstant;
import app.foxochat.constant.UserConstant;
import app.foxochat.dto.gateway.EventDTO;
import app.foxochat.exception.user.UserUnauthorizedException;
import app.foxochat.model.Session;
import app.foxochat.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class EventHandler extends TextWebSocketHandler {

    private final EventHandlerRegistry handlerRegistry;

    private final ObjectMapper objectMapper;

    @Getter
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    private final UserService userService;

    public EventHandler(EventHandlerRegistry handlerRegistry, ObjectMapper objectMapper, UserService userService) {
        this.handlerRegistry = handlerRegistry;
        this.objectMapper = objectMapper;
        this.userService = userService;

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

        Runnable task = () -> sessions.values().forEach(session -> {
            long lastPingTimestamp = session.getLastPingTimestamp();

            long timeout = (GatewayConstant.HEARTBEAT_INTERVAL + GatewayConstant.HEARTBEAT_TIMEOUT);

            if (lastPingTimestamp < (System.currentTimeMillis() - timeout)) {
                try {
                    session.getWebSocketSession().close(CloseCodeConstant.HEARTBEAT_TIMEOUT);
                    log.debug("Session closed due to heartbeat timeout: {}", session.getWebSocketSession().getId());
                } catch (IOException e) {
                    log.error("Error closing session: {}", session.getWebSocketSession().getId(), e);
                    throw new RuntimeException(e);
                }
            }
        });

        executor.scheduleAtFixedRate(task, 0, 30, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        log.debug("Connection for session ({}) established", session.getId());
        sessions.put(session.getId(), new Session(session));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        log.debug("Connection for session ({}) closed with status {} ({})",
                session.getId(),
                status.getReason(),
                status.getCode());

        Session userSession = sessions.get(session.getId());

        if (userSession.isAuthenticated()) {
            userService.setStatus(userSession.getUserId(), UserConstant.Status.OFFLINE.getStatus());
        }

        sessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        try {
            EventDTO payload = objectMapper.readValue(message.getPayload(), EventDTO.class);
            int opcode = payload.getOp();

            BaseHandler handler = handlerRegistry.getHandler(opcode);

            if (handler != null) {
                handler.handle(session, sessions, payload);
                log.debug("Handling {} event with opcode {}", payload.getT(), payload.getOp());
            }
        } catch (UserUnauthorizedException e) {
            log.error(ExceptionConstant.Messages.SERVER_EXCEPTION.getValue(), null, null, message);

            session.close(CloseCodeConstant.UNAUTHORIZED);
        } catch (Exception e) {
            log.error(ExceptionConstant.Messages.SERVER_EXCEPTION.getValue(), null, null, message);
            log.error(ExceptionConstant.Messages.SERVER_EXCEPTION_STACKTRACE.getValue(), e);
        }
    }
}
