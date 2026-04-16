/*
 * $Id$
 *
 * Copyright (C) 2003-2015 JNode.org
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; If not, write to the Free Software Foundation, Inc., 
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
 
package org.jnode.net.ipv4.dhcp;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.jnode.net.ipv4.IPv4Address;
import org.jnode.net.ipv4.bootp.AbstractBOOTPClient;
import org.jnode.net.ipv4.bootp.BOOTPHeader;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * System independent base class.
 * Implementations should override doConfigure.
 *
 * @author markhale
 */
public class AbstractDHCPClient extends AbstractBOOTPClient {

    private static final Logger log = LogManager.getLogger(AbstractDHCPClient.class);

    /**
     * Create a DHCP discovery packet
     */
    protected DatagramPacket createRequestPacket(BOOTPHeader hdr) throws IOException {
        DHCPMessage msg = new DHCPMessage(hdr, DHCPMessage.DHCPDISCOVER);
        return msg.toDatagramPacket();
    }

    protected boolean processResponse(int transactionID, DatagramPacket packet) throws IOException {
        DHCPMessage msg = new DHCPMessage(packet);
        BOOTPHeader hdr = msg.getHeader();
        if (hdr.getOpcode() != BOOTPHeader.BOOTREPLY) {
            // Not a response
            return false;
        }
        if (hdr.getTransactionID() != transactionID) {
            // Not for me
            return false;
        }

        // debug the DHCP message
        if (log.isDebugEnabled()) {
            log.debug("Got Client IP address  : " + hdr.getClientIPAddress());
            log.debug("Got Your IP address    : " + hdr.getYourIPAddress());
            log.debug("Got Server IP address  : " + hdr.getServerIPAddress());
            log.debug("Got Gateway IP address : " + hdr.getGatewayIPAddress());
        }
        
        // Log all DHCP options (including unsupported ones) for diagnostics
        boolean hasUnsupportedOptions = false;
        for (int n = 1; n < 255; n++) {
            byte[] value = msg.getOption(n);
            if (value != null) {
                String optionDesc = getOptionDescription(n);
                if (optionDesc.startsWith("Unknown")) {
                    log.warn("DHCP Option " + n + " (unsupported): " + optionDesc);
                    hasUnsupportedOptions = true;
                } else if (log.isDebugEnabled()) {
                    if (value.length == 1) {
                        log.debug("DHCP Option " + n + " (" + optionDesc + "): " + (int) (value[0]));
                    } else if (value.length == 2) {
                        log.debug("DHCP Option " + n + " (" + optionDesc + "): " + ((value[0] << 8) | value[1]));
                    } else if (value.length == 4) {
                        log.debug("DHCP Option " + n + " (" + optionDesc + "): " +
                                InetAddress.getByAddress(value).toString());
                    } else {
                        log.debug("DHCP Option " + n + " (" + optionDesc + "): " + new String(value));
                    }
                }
            }
        }
        
        if (hasUnsupportedOptions) {
            log.info("Note: Some unsupported DHCP options were received but ignored");
        }

        switch (msg.getMessageType()) {
            case DHCPMessage.DHCPOFFER:
                byte[] serverID = msg.getOption(DHCPMessage.SERVER_IDENTIFIER_OPTION);
                byte[] requestedIP = hdr.getYourIPAddress().getAddress();
                hdr = new BOOTPHeader(
                        BOOTPHeader.BOOTREQUEST, transactionID, 0, 
                        hdr.getClientIPAddress(), hdr.getClientHwAddress());
                msg = new DHCPMessage(hdr, DHCPMessage.DHCPREQUEST);
                msg.setOption(DHCPMessage.REQUESTED_IP_ADDRESS_OPTION, requestedIP);
                msg.setOption(DHCPMessage.SERVER_IDENTIFIER_OPTION, serverID);
                packet = msg.toDatagramPacket();
                packet.setAddress(IPv4Address.BROADCAST_ADDRESS);
                packet.setPort(SERVER_PORT);
                socket.send(packet);
                break;
            case DHCPMessage.DHCPACK:
                doConfigure(msg);
                return true;
            case DHCPMessage.DHCPNAK:
                break;
        }
        return false;
    }

    protected void doConfigure(DHCPMessage msg) throws IOException {
        doConfigure(msg.getHeader());
    }

    /**
     * Get a human-readable description of a DHCP option code
     */
    private String getOptionDescription(int optionCode) {
        switch (optionCode) {
            case 1: return "Subnet Mask";
            case 2: return "Time Offset";
            case 3: return "Router";
            case 4: return "Time Server";
            case 5: return "Name Server";
            case 6: return "DNS Server";
            case 7: return "Log Server";
            case 8: return "Cookie Server";
            case 9: return "LPR Server";
            case 12: return "Host Name";
            case 15: return "Domain Name";
            case 23: return "TTL";
            case 31: return "Perform Router Discovery";
            case 33: return "Static Route";
            case 34: return "Trailer Encapsulation";
            case 36: return "Ethernet Encapsulation";
            case 39: return "TCP Keepalive";
            case 50: return "Requested IP Address";
            case 51: return "Lease Time";
            case 52: return "Option Overload";
            case 53: return "Message Type";
            case 54: return "Server Identifier";
            case 56: return "Message";
            case 57: return "Max Packet Size";
            case 58: return "Renewal Time";
            case 59: return "Rebinding Time";
            case 61: return "Client Identifier";
            case 66: return "TFTP Server";
            case 69: return "SMTP Server";
            case 70: return "POP3 Server";
            case 71: return "NNTP Server";
            case 72: return "WWW Server";
            case 73: return "Finger Server";
            case 74: return "IRC Server";
            case 130: return "Plugin Loader";
            case 255: return "End";
            default: return "Unknown option " + optionCode;
        }
    }
}
