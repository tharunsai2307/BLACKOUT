package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.blackout.emergency.relay.RelayPayloadModels;
import com.blackout.emergency.utils.Constants;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests verifying Phase 4 protocol payload serialization and deserialization:
 * - MESSAGE_INVENTORY
 * - DELIVERY_ACK
 * - DELIVERY_REJECTED
 */
public class RelayPayloadModelsTest {

    @Test
    public void testInventorySerializationRoundTrip() throws Exception {
        List<String> ids = Arrays.asList("id-1", "id-2", "id-3");
        RelayPayloadModels.InventoryPayload original = new RelayPayloadModels.InventoryPayload("DEV-SENDER", ids);

        byte[] bytes = original.toBytes();
        assertNotNull(bytes);
        assertEquals(Constants.MESSAGE_TYPE_INVENTORY, RelayPayloadModels.peekMessageType(bytes));

        RelayPayloadModels.InventoryPayload parsed = RelayPayloadModels.InventoryPayload.fromBytes(bytes);
        assertNotNull(parsed);
        assertEquals(Constants.PROTOCOL_VERSION, parsed.getProtocolVersion());
        assertEquals("DEV-SENDER", parsed.getSenderDeviceId());
        assertEquals(3, parsed.getKnownMessageIds().size());
        assertEquals("id-1", parsed.getKnownMessageIds().get(0));
        assertEquals("id-2", parsed.getKnownMessageIds().get(1));
        assertEquals("id-3", parsed.getKnownMessageIds().get(2));
    }

    @Test
    public void testInventorySerializationEmptyList() throws Exception {
        RelayPayloadModels.InventoryPayload original = new RelayPayloadModels.InventoryPayload("DEV-EMPTY", Collections.emptyList());
        byte[] bytes = original.toBytes();
        RelayPayloadModels.InventoryPayload parsed = RelayPayloadModels.InventoryPayload.fromBytes(bytes);
        assertNotNull(parsed);
        assertTrue(parsed.getKnownMessageIds().isEmpty());
    }

    @Test
    public void testDeliveryAckRoundTrip() throws Exception {
        long now = System.currentTimeMillis();
        RelayPayloadModels.DeliveryAckPayload original = new RelayPayloadModels.DeliveryAckPayload(
                Constants.PROTOCOL_VERSION, "msg-uuid-99", "DEV-RECEIVER", now, 2
        );

        byte[] bytes = original.toBytes();
        assertEquals(Constants.MESSAGE_TYPE_DELIVERY_ACK, RelayPayloadModels.peekMessageType(bytes));

        RelayPayloadModels.DeliveryAckPayload parsed = RelayPayloadModels.DeliveryAckPayload.fromBytes(bytes);
        assertNotNull(parsed);
        assertEquals("msg-uuid-99", parsed.getMessageId());
        assertEquals("DEV-RECEIVER", parsed.getReceiverDeviceId());
        assertEquals(now, parsed.getAcknowledgedAt());
        assertEquals(2, parsed.getHopNumber());
    }

    @Test
    public void testDeliveryRejectRoundTrip() throws Exception {
        RelayPayloadModels.DeliveryRejectPayload original = new RelayPayloadModels.DeliveryRejectPayload(
                "msg-uuid-rejected", "DEV-RECEIVER", "EXPIRED_MESSAGE"
        );

        byte[] bytes = original.toBytes();
        assertEquals(Constants.MESSAGE_TYPE_DELIVERY_REJECTED, RelayPayloadModels.peekMessageType(bytes));

        RelayPayloadModels.DeliveryRejectPayload parsed = RelayPayloadModels.DeliveryRejectPayload.fromBytes(bytes);
        assertNotNull(parsed);
        assertEquals("msg-uuid-rejected", parsed.getMessageId());
        assertEquals("DEV-RECEIVER", parsed.getReceiverDeviceId());
        assertEquals("EXPIRED_MESSAGE", parsed.getReason());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInventoryRejectsEmptyPayload() throws Exception {
        RelayPayloadModels.InventoryPayload.fromBytes(new byte[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeliveryAckRejectsMissingMessageId() throws Exception {
        String invalidJson = "{\"protocolVersion\":1,\"messageType\":\"DELIVERY_ACK\"}";
        RelayPayloadModels.DeliveryAckPayload.fromBytes(invalidJson.getBytes());
    }
}
