package moomoo.hgtp.server.protocol.hgtp.message.request;

import moomoo.hgtp.server.protocol.hgtp.exception.HgtpException;
import moomoo.hgtp.server.protocol.hgtp.message.base.HgtpHeader;
import moomoo.hgtp.server.protocol.hgtp.message.base.HgtpMessage;
import moomoo.hgtp.server.protocol.hgtp.message.base.content.HgtpRoomManagerContent;


public class HgtpInviteUserFromRoomRequest extends HgtpMessage {

    private final HgtpHeader hgtpHeader;
    private final HgtpRoomManagerContent hgtpContext;

    public HgtpInviteUserFromRoomRequest(byte[] data) throws HgtpException {
        if (data.length >= HgtpHeader.HGTP_HEADER_SIZE + 12) {
            int index = 0;

            byte[] headerByteData = new byte[HgtpHeader.HGTP_HEADER_SIZE];
            System.arraycopy(data, index, headerByteData, 0, headerByteData.length);
            this.hgtpHeader = new HgtpHeader(headerByteData);
            index += headerByteData.length;

            byte[] contextByteData = new byte[hgtpHeader.getBodyLength()];
            System.arraycopy(data, index, contextByteData, 0, contextByteData.length);
            this.hgtpContext = new HgtpRoomManagerContent(contextByteData);
        } else {
            this.hgtpHeader = null;
            this.hgtpContext = null;
        }
    }

    public HgtpInviteUserFromRoomRequest(short magicCookie, short messageType, String userId, int seqNumber, long timeStamp, String roomId, String peerUserId) {
        int bodyLength = 12 + 8;

        this.hgtpHeader = new HgtpHeader(magicCookie, messageType, messageType, userId, seqNumber, timeStamp, bodyLength);
        this.hgtpContext = new HgtpRoomManagerContent(roomId, peerUserId);
    }

    @Override
    public byte[] getByteData() {
        byte[] data = new byte[HgtpHeader.HGTP_HEADER_SIZE + this.hgtpHeader.getBodyLength()];
        int index = 0;

        byte[] headerByteData = this.hgtpHeader.getByteData();
        System.arraycopy(headerByteData, 0, data, index, headerByteData.length);
        index += headerByteData.length;

        byte[] contextByteData = this.hgtpContext.getByteData();
        System.arraycopy(contextByteData, 0, data, index, contextByteData.length);

        return data;
    }

    public HgtpHeader getHgtpHeader() {return hgtpHeader;}

    public HgtpRoomManagerContent getHgtpContext() {return hgtpContext;}
}