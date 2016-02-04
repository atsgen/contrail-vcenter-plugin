/**
 * Copyright (c) 2016 Juniper Networks, Inc. All rights reserved.
 */

package net.juniper.contrail.vcenter;

import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.io.IOException;
import java.util.SortedMap;
import org.apache.log4j.Logger;
import org.powermock.core.classloader.annotations.PrepareForTest;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.MethodSorters;
import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.ApiConnectorMock;
import net.juniper.contrail.api.types.VirtualNetwork;
import net.juniper.contrail.api.types.Project;

@RunWith(JUnit4.class)
//@RunWith(PowerMockRunner.class)
@PrepareForTest(VRouterNotifier.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VirtualNetworkInfoTest extends TestCase {
    public static VncDB vncDB;
    public static ApiConnector api;
    private static final Logger s_logger =
            Logger.getLogger(VirtualNetworkInfoTest.class);

    private final static String vnUuid1         = UUID.randomUUID().toString();
    private final static String vnName1         = "BLUE";
    private final static String subnetAddr1     = "192.168.0.0";
    private final static String subnetMask1     = "255.255.0.0";
    private final static String gatewayAddr1    = "192.168.0.1";
    private final static String range1          = "192.168.0.2#430";
    private final static short primaryVlanId1   = 100;
    private final static short isolatedVlanId1  = 101;
    private final static boolean externalIpam1  = false;
    private final static boolean ipPoolEnabled1  = true;

    public static VirtualNetworkInfo BLUE = new VirtualNetworkInfo(vnUuid1, vnName1,
            externalIpam1, ipPoolEnabled1,
            subnetAddr1, subnetMask1, range1, gatewayAddr1,
            primaryVlanId1, isolatedVlanId1);

    private final static String vnUuid2         = UUID.randomUUID().toString();
    private final static String vnName2         = "RED";
    private final static String subnetAddr2     = "192.168.2.0";
    private final static String subnetMask2     = "255.255.255.0";
    private final static String gatewayAddr2    = "192.168.2.1";
    private final static String range2          = "192.168.2.2#230";
    private final static short primaryVlanId2   = 200;
    private final static short isolatedVlanId2  = 201;
    private final static boolean externalIpam2  = false;
    private final static boolean ipPoolEnabled2  = true;

    public static VirtualNetworkInfo RED = new VirtualNetworkInfo(vnUuid2, vnName2,
            externalIpam2, ipPoolEnabled2,
            subnetAddr2, subnetMask2, range2, gatewayAddr2,
            primaryVlanId2, isolatedVlanId2);
    
    public static VirtualNetworkInfo newInstance(int selection) {
        switch (selection) {
        case 1:
            return new VirtualNetworkInfo(BLUE);
        case 2:
            return new VirtualNetworkInfo(RED);
        default:
            ;
        }
        return new VirtualNetworkInfo(BLUE);
    }
    
    @Before
    public void globalSetUp() throws IOException {
        api   = new ApiConnectorMock(null, 0);
        assertNotNull(api);

        // Create default-domain,default-project
        Project vProject = new Project();
        vProject.setName("default-project");
        try {
            if (!api.create(vProject)) {
                s_logger.error("Unable to create project: " + vProject.getName());
                fail("default-project creation failed");
                return;
            }
        } catch (IOException e) { 
            s_logger.error("Exception : " + e);
            e.printStackTrace();
            fail("default-project creation failed");
            return;
        }

        // Setup vnc object
        vncDB = new VncDB(null,0, Mode.VCENTER_ONLY);
        vncDB.setApiConnector(api);
        assertNotNull(vncDB.getApiConnector());
        assertTrue(vncDB.isVncApiServerAlive());
        assertTrue(vncDB.Initialize());
        
        MainDB.vncDB = vncDB;
    }

    public static VirtualNetwork verifyVirtualNetworkPresent(VirtualNetworkInfo vnInfo) 
            throws IOException {
        
        VirtualNetwork vn = (VirtualNetwork) api.findById(VirtualNetwork.class, vnInfo.getUuid());
        assertNotNull(vn);
        assertEquals(vn.getUuid(), vnInfo.getUuid());
        assertEquals(vn.getName(), vnInfo.getName());
        
        return vn;
    }

    public static void verifyVirtualNetworkAbsent(VirtualNetworkInfo vnInfo) throws IOException {
        VirtualNetwork vn = (VirtualNetwork)  api.findById(VirtualNetwork.class, vnInfo.getUuid());
        assertNull(vn);
    }

    @Test
    public void testVirtualNetworkCreateDelete() throws IOException {
        
        VirtualNetworkInfo vnInfo = newInstance(1);
        
        try {
            vnInfo.create(vncDB);
        } catch (Exception e) {
            fail("Cannot create VN " + vnInfo);
        }
        
        // Verify virtual-network creation
        assertTrue(MainDB.getVNs().containsKey(vnInfo.getUuid()));
        verifyVirtualNetworkPresent(vnInfo);
        
        try {
            vnInfo.delete(vncDB);
        } catch (Exception e) {
            fail("Cannot delete VN " + vnInfo);
            e.printStackTrace();
        }

        assertFalse(MainDB.getVNs().containsKey(vnInfo.getUuid()));
        verifyVirtualNetworkAbsent(vnInfo);
    }
    
    @Test
    public void testSyncVirtualNetwork() throws IOException {
        SortedMap<String, VirtualNetworkInfo> newVNs =
                new ConcurrentSkipListMap<String, VirtualNetworkInfo>();
        VirtualNetworkInfo newVnInfo = newInstance(1);
        newVNs.put(newVnInfo.getUuid(), newVnInfo);
        
        SortedMap<String, VirtualNetworkInfo> oldVNs =
                new ConcurrentSkipListMap<String, VirtualNetworkInfo>();

        MainDB.sync(oldVNs, newVNs);
             
        // Verify virtual-network has been created
        verifyVirtualNetworkPresent(newVnInfo);
        
        oldVNs = vncDB.readVirtualNetworks();
        newVNs = new ConcurrentSkipListMap<String, VirtualNetworkInfo>();

        MainDB.sync(oldVNs, newVNs);
        
        // Verify virtual-network has been deleted
        verifyVirtualNetworkAbsent(newVnInfo);
    }
}
