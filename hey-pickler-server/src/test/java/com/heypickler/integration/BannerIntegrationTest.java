package com.heypickler.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BannerIntegrationTest extends IntegrationTestConfig {

    @Test
    void bannerCrud_FullLifecycle() {
        HttpHeaders headers = adminAuthHeaders();

        // Create
        Map<String, Object> createBody = Map.of(
                "imageUrl", "https://test.com/banner.jpg",
                "linkUrl", "https://test.com/link",
                "sortOrder", 1,
                "status", "ACTIVE"
        );
        HttpEntity<Map<String, Object>> createReq = new HttpEntity<>(createBody, headers);
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
                "/api/admin/banners", createReq, Map.class);

        assertEquals(0, resultCode(createResp));
        @SuppressWarnings("unchecked")
        Map<String, Object> createData = (Map<String, Object>) resultData(createResp);
        Long bannerId = ((Number) createData.get("id")).longValue();
        assertNotNull(bannerId);

        // List (admin)
        HttpEntity<Void> listReq = new HttpEntity<>(headers);
        ResponseEntity<Map> listResp = restTemplate.exchange(
                "/api/admin/banners", HttpMethod.GET, listReq, Map.class);

        assertEquals(0, resultCode(listResp));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> banners = (List<Map<String, Object>>) resultData(listResp);
        assertFalse(banners.isEmpty());

        // Update to INACTIVE
        Map<String, Object> updateBody = Map.of(
                "imageUrl", "https://test.com/updated.jpg",
                "linkUrl", "https://test.com/link",
                "sortOrder", 99,
                "status", "INACTIVE"
        );
        HttpEntity<Map<String, Object>> updateReq = new HttpEntity<>(updateBody, headers);
        ResponseEntity<Map> updateResp = restTemplate.exchange(
                "/api/admin/banners/" + bannerId, HttpMethod.PUT, updateReq, Map.class);
        assertEquals(0, resultCode(updateResp));

        // App list (only ENABLED - ours is DISABLED)
        ResponseEntity<Map> appListResp = restTemplate.getForEntity(
                "/api/app/banners", Map.class);
        assertEquals(0, resultCode(appListResp));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> appBanners = (List<Map<String, Object>>) resultData(appListResp);
        assertTrue(appBanners.stream().noneMatch(b ->
                ((Number) b.get("id")).longValue() == bannerId));

        // Re-enable
        Map<String, Object> enableBody = Map.of(
                "imageUrl", "https://test.com/updated.jpg",
                "linkUrl", "https://test.com/link",
                "sortOrder", 1,
                "status", "ACTIVE"
        );
        HttpEntity<Map<String, Object>> enableReq = new HttpEntity<>(enableBody, headers);
        ResponseEntity<Map> enableResp = restTemplate.exchange(
                "/api/admin/banners/" + bannerId, HttpMethod.PUT, enableReq, Map.class);
        assertEquals(0, resultCode(enableResp));

        // Verify app can now see it
        ResponseEntity<Map> appListResp2 = restTemplate.getForEntity(
                "/api/app/banners", Map.class);
        assertEquals(0, resultCode(appListResp2));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> appBanners2 = (List<Map<String, Object>>) resultData(appListResp2);
        assertTrue(appBanners2.stream().anyMatch(b ->
                ((Number) b.get("id")).longValue() == bannerId));

        // Delete
        HttpEntity<Void> deleteReq = new HttpEntity<>(headers);
        ResponseEntity<Map> deleteResp = restTemplate.exchange(
                "/api/admin/banners/" + bannerId, HttpMethod.DELETE, deleteReq, Map.class);
        assertEquals(0, resultCode(deleteResp));
    }
}
