package moomoo.hgtp.server.protocol.hgtp.message.request.handler;

import moomoo.hgtp.server.protocol.hgtp.message.base.HgtpHeader;
import moomoo.hgtp.server.protocol.hgtp.message.base.HgtpMessageType;
import moomoo.hgtp.server.protocol.hgtp.message.base.content.HgtpRegisterContent;
import moomoo.hgtp.server.protocol.hgtp.message.base.content.HgtpRoomContent;
import moomoo.hgtp.server.protocol.hgtp.message.request.*;
import moomoo.hgtp.server.protocol.hgtp.message.response.HgtpCommonResponse;
import moomoo.hgtp.server.protocol.hgtp.message.response.HgtpUnauthorizedResponse;
import moomoo.hgtp.server.protocol.hgtp.message.response.handler.HgtpResponseHandler;
import moomoo.hgtp.server.service.AppInstance;
import moomoo.hgtp.server.session.SessionManager;
import moomoo.hgtp.server.session.base.RoomInfo;
import moomoo.hgtp.server.session.base.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HgtpRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(HgtpRequestHandler.class);
    private static final String LOG_FORMAT = "({}) () () RECV HGTP MSG [{}]";

    private static AppInstance appInstance = AppInstance.getInstance();

    private final HgtpResponseHandler hgtpResponseHandler = new HgtpResponseHandler();

    private SessionManager sessionManager = SessionManager.getInstance();

    public HgtpRequestHandler() {
        // nothing
    }

    public void registerRequestProcessing(HgtpRegisterRequest hgtpRegisterRequest) {
        HgtpHeader hgtpHeader = hgtpRegisterRequest.getHgtpHeader();
        HgtpRegisterContent hgtpRegisterContent = hgtpRegisterRequest.getHgtpContent();

        String userId = hgtpHeader.getUserId();

        log.debug(LOG_FORMAT, userId, hgtpRegisterRequest);


        // 첫 번째 Register Request
        short messageType = HgtpMessageType.UNKNOWN;
        if (hgtpRegisterContent.getNonce().equals("")) {
            // userInfo 생성
            UserInfo userInfo = sessionManager.addUserInfo(
                    userId, hgtpRegisterContent.getListenIp() , hgtpRegisterContent.getListenPort(), hgtpRegisterContent.getExpires()
            );

            if (userInfo == null) {
                // userInfo 생성 오류
                messageType = HgtpMessageType.BAD_REQUEST;
                log.debug("({}) () () UserInfo already exist.", userId);
            } else if (sessionManager.getUserInfoSize() > appInstance.getConfigManager().getUserMaxSize()) {
                // 최대 userInfo 초과
                messageType = HgtpMessageType.SERVER_UNAVAILABLE;
                log.debug("({}) () () Unavailable add UserInfo", userId);
            }

            if (messageType == HgtpMessageType.UNKNOWN) {
                HgtpUnauthorizedResponse hgtpUnauthorizedResponse = new HgtpUnauthorizedResponse(
                        AppInstance.MAGIC_COOKIE, HgtpMessageType.UNAUTHORIZED, hgtpHeader.getRequestType(),
                        userId, hgtpHeader.getSeqNumber() + AppInstance.SEQ_INCREMENT, appInstance.getTimeStamp(), AppInstance.MD5_REALM);

                hgtpResponseHandler.sendUnauthorizedResponse(hgtpUnauthorizedResponse);
            } else {
                // UNAUTHORIZED 이외인 경우 UserInfo 삭제
                HgtpCommonResponse hgtpCommonResponse = new HgtpCommonResponse(
                        AppInstance.MAGIC_COOKIE, messageType, hgtpHeader.getRequestType(),
                        userId, hgtpHeader.getSeqNumber() + AppInstance.SEQ_INCREMENT, appInstance.getTimeStamp());

                hgtpResponseHandler.sendCommonResponse(hgtpCommonResponse);
                if (userInfo != null) {
                    sessionManager.deleteUserInfo(userInfo.getUserId());
                }
            }
        }
        // 두 번째 Register Request
        else {
            // nonce 일치하면 userInfo 유지
            if (hgtpRegisterContent.getNonce().equals(appInstance.getServerNonce())) {
                messageType = HgtpMessageType.OK;
            }
            // 불일치 시 userInfo 삭제
            else {
                messageType = HgtpMessageType.FORBIDDEN;
            }

            HgtpCommonResponse hgtpCommonResponse = new HgtpCommonResponse(
                    AppInstance.MAGIC_COOKIE, messageType, hgtpHeader.getRequestType(),
                    userId, hgtpHeader.getSeqNumber() + AppInstance.SEQ_INCREMENT, appInstance.getTimeStamp());

            hgtpResponseHandler.sendCommonResponse(hgtpCommonResponse);

            UserInfo userInfo = sessionManager.getUserInfo(userId);
            if (userInfo != null && messageType == HgtpMessageType.FORBIDDEN) {
                sessionManager.deleteUserInfo(userInfo.getUserId());
            }
        }
    }

    public void unregisterRequestProcessing(HgtpUnregisterRequest hgtpUnregisterRequest) {
        HgtpHeader hgtpHeader = hgtpUnregisterRequest.getHgtpHeader();

        String userId = hgtpHeader.getUserId();

        log.debug(LOG_FORMAT, userId, hgtpUnregisterRequest);

        UserInfo userInfo = sessionManager.getUserInfo(userId);
        if (userInfo == null) {
            log.debug("{} UserInfo is unregister", userId);
            return;
        }

        short messageType = HgtpMessageType.UNKNOWN;
        if (sessionManager.getRoomInfo(userInfo.getRoomId()) != null) {
            // userInfo 가 아직 roomInfo 에 존재
            messageType = HgtpMessageType.BAD_REQUEST;
            log.debug("({}) () () UserInfo already exist.", userId);
        } else {
            messageType = HgtpMessageType.OK;
        }

        HgtpCommonResponse hgtpCommonResponse = new HgtpCommonResponse(
                AppInstance.MAGIC_COOKIE, messageType, hgtpHeader.getRequestType(),
                userId, hgtpHeader.getSeqNumber() + AppInstance.SEQ_INCREMENT, appInstance.getTimeStamp());

        hgtpResponseHandler.sendCommonResponse(hgtpCommonResponse);

        if (messageType == HgtpMessageType.OK) {
            sessionManager.deleteUserInfo(userId);
        }
    }

    public void createRoomRequestProcessing(HgtpCreateRoomRequest hgtpCreateRoomRequest) {
        HgtpHeader hgtpHeader = hgtpCreateRoomRequest.getHgtpHeader();
        HgtpRoomContent hgtpRoomContent = hgtpCreateRoomRequest.getHgtpContent();

        String roomId = hgtpRoomContent.getRoomId();
        String userId = hgtpHeader.getUserId();

        log.debug(LOG_FORMAT, userId, hgtpCreateRoomRequest);


        if (sessionManager.getUserInfo(userId) == null) {
            log.debug("{} UserInfo is unregister", userId);
            return;
        }

        short messageType = HgtpMessageType.OK;
        if (roomId.equals("")) {
            messageType = HgtpMessageType.BAD_REQUEST;
            log.debug("({}) ({}) () RoomId is null", userId, roomId);
        } else {
            RoomInfo roomInfo = sessionManager.addRoomInfo(roomId, userId);

            if (roomInfo == null) {
                messageType = HgtpMessageType.BAD_REQUEST;
                log.debug("({}) ({}) () RoomInfo already exist.", userId, roomId);
            } else if (sessionManager.getRoomInfoSize() > appInstance.getConfigManager().getRoomMaxSize()) {
                // 최대 roomInfo 초과
                messageType = HgtpMessageType.SERVER_UNAVAILABLE;
                log.debug("({}) () () Unavailable add RoomInfo", userId);
            }
        }

        HgtpCommonResponse hgtpCommonResponse = new HgtpCommonResponse(
                AppInstance.MAGIC_COOKIE, messageType, hgtpHeader.getRequestType(),
                userId, hgtpHeader.getSeqNumber() + AppInstance.SEQ_INCREMENT, appInstance.getTimeStamp());

        hgtpResponseHandler.sendCommonResponse(hgtpCommonResponse);
        if (messageType == HgtpMessageType.SERVER_UNAVAILABLE) {
            sessionManager.deleteRoomInfo(roomId);
        }
    }

    public void deleteRoomRequestProcessing(HgtpDeleteRoomRequest hgtpDeleteRoomRequest) {
        HgtpHeader hgtpHeader = hgtpDeleteRoomRequest.getHgtpHeader();
        HgtpRoomContent hgtpRoomContent = hgtpDeleteRoomRequest.getHgtpContent();

        String roomId = hgtpRoomContent.getRoomId();
        String userId = hgtpHeader.getUserId();

        log.debug(LOG_FORMAT, userId, hgtpDeleteRoomRequest);


        if (sessionManager.getUserInfo(userId) == null) {
            log.debug("{} UserInfo is unregister", userId);
        }


        short messageType;
        if (roomId.equals("")) {
            messageType = HgtpMessageType.BAD_REQUEST;
            log.debug("({}) ({}) () RoomId is null", userId, roomId);
        } else {
            RoomInfo roomInfo = sessionManager.getRoomInfo(roomId);

            if (roomInfo == null) {
                messageType = HgtpMessageType.BAD_REQUEST;
                log.debug("({}) ({}) () RoomInfo already deleted.", userId, roomId);
            } else if (!roomInfo.getManagerId().equals(userId)) {
                messageType = HgtpMessageType.BAD_REQUEST;
                log.debug("({}) ({}) () UserInfo is not room manager.", userId, roomId);
            } else {
                // 최대 roomInfo 초과
                messageType = HgtpMessageType.OK;
                log.debug("({}) ({}) () RoomInfo was delete.", userId, roomId);
            }
        }

        HgtpCommonResponse hgtpCommonResponse = new HgtpCommonResponse(
                AppInstance.MAGIC_COOKIE, messageType, hgtpHeader.getRequestType(),
                userId, hgtpHeader.getSeqNumber() + AppInstance.SEQ_INCREMENT, appInstance.getTimeStamp());

        hgtpResponseHandler.sendCommonResponse(hgtpCommonResponse);
        if (messageType == HgtpMessageType.OK) {
            sessionManager.deleteRoomInfo(roomId);
        }
    }

    public boolean joinRoomRequestProcessing(HgtpJoinRoomRequest hgtpJoinRoomRequest) {
        log.debug(LOG_FORMAT, hgtpJoinRoomRequest.getHgtpHeader().getUserId(), hgtpJoinRoomRequest);
        return true;
    }

    public boolean exitRoomRequestProcessing(HgtpExitRoomRequest hgtpExitRoomRequest) {
        log.debug(LOG_FORMAT, hgtpExitRoomRequest.getHgtpHeader().getUserId(), hgtpExitRoomRequest);
        return true;
    }

    public boolean inviteUserFromRoomRequestProcessing(HgtpInviteUserFromRoomRequest hgtpInviteUserFromRoomRequest) {
        log.debug(LOG_FORMAT, hgtpInviteUserFromRoomRequest.getHgtpHeader().getUserId(), hgtpInviteUserFromRoomRequest);
        return true;
    }

    public boolean removeUserFromRoomRequestProcessing(HgtpRemoveUserFromRoomRequest hgtpRemoveUserFromRoomRequest) {
        log.debug(LOG_FORMAT, hgtpRemoveUserFromRoomRequest.getHgtpHeader().getUserId(), hgtpRemoveUserFromRoomRequest);
        return true;
    }

}
