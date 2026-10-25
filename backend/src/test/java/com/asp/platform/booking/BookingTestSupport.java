package com.asp.platform.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Workshops with the booking add-on on, services listed online and settings that make slots predictable: open
/// every day 08:00–20:00 in one-hour slots, no lead time, cancel until the slot starts.
///
/// Public requests come from a new random client address each time, so the test rate limits
/// (`src/test/resources/config/application.yml`) only apply where a test sends from one address on purpose.
public abstract class BookingTestSupport extends ApiTestSupport {

    protected static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

    protected record Workshop(String id, String slug, String owner) {

        protected String publicPath(String path) {
            return "/api/v1/public/" + slug + "/booking" + path;
        }
    }

    /// A workshop with the booking add-on on (when `booking`), its owner signed in.
    protected Workshop workshop(String name, boolean booking) throws Exception {
        String body = provisionWorkshop(name, uniqueEmail("dono")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String owner = activate(JsonPath.read(body, "$.activationPath"), "Owner");
        Workshop w = new Workshop(JsonPath.read(body, "$.workshop.id"), JsonPath.read(body, "$.workshop.slug"), owner);
        if (booking) {
            String admin = platformAdmin();
            long version = versionAt(admin, "/api/v1/admin/workshops/" + w.id());
            call(put("/api/v1/admin/workshops/" + w.id() + "/add-ons")
                    .content(withVersion("{\"booking\":true,\"shop\":false}", version)), admin)
                    .andExpect(status().isOk());
        }
        return w;
    }

    /// Invites a team member with this role and returns their token; their name is the role in lower case.
    protected String member(Workshop w, String role) throws Exception {
        String email = uniqueEmail(role.toLowerCase(java.util.Locale.ROOT));
        invite(w.owner(), role, email);
        return login(email, PASSWORD);
    }

    /// A fixed-price service; returns its id.
    protected String service(String owner, String name, long priceCents, boolean listedOnline, boolean active)
            throws Exception {
        return idOf(call(post("/api/v1/catalogue/services").content("""
                {"name":"%s","description":"Descrição","category":"Manutenção","standardHours":1,"pricing":"FIXED",
                 "priceCents":%d,"vatRate":23,"durationMinutes":60,"active":%s,"listedOnline":%s}"""
                .formatted(name, priceCents, active, listedOnline)), owner).andExpect(status().isCreated()));
    }

    /// Open every day 08:00–20:00, one-hour slots, no lead time, 30 days ahead, cancel until the slot starts.
    protected void openEveryDay(Workshop w, int carsPerSlot) throws Exception {
        saveSettings(w, carsPerSlot, 0, "[]");
    }

    protected void saveSettings(Workshop w, int carsPerSlot, int cancelUntilHours, String closures) throws Exception {
        StringBuilder hours = new StringBuilder();
        for (int day = 1; day <= 7; day++) {
            hours.append(day == 1 ? "" : ",").append("{\"weekday\":%d,\"opens\":\"08:00\",\"closes\":\"20:00\"}"
                    .formatted(day));
        }
        call(put("/api/v1/booking/settings").content(withVersion("""
                {"slotMinutes":60,"carsPerSlot":%d,"leadTimeHours":0,"horizonDays":30,"cancelUntilHours":%d,
                 "openingHours":[%s],"closures":%s}""".formatted(carsPerSlot, cancelUntilHours, hours, closures),
                versionAt(w.owner(), "/api/v1/booking/settings"))), w.owner())
                .andExpect(status().isOk());
    }

    /// Tomorrow in Lisbon: with the settings above, a day with twelve slots from 08:00 local, all in the future.
    protected static LocalDate tomorrow() {
        return LocalDate.now(LISBON).plusDays(1);
    }

    /// The free slots' start instants of a day, as the public page lists them.
    protected List<String> freeSlots(Workshop w, LocalDate day) throws Exception {
        return JsonPath.read(publicCall(get(w.publicPath("/slots?from=" + day + "&days=1")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$[0].slots[*].start");
    }

    /// `POST /bookings` with valid contact data.
    protected ResultActions book(Workshop w, String slotStart, String... serviceIds) throws Exception {
        return publicCall(post(w.publicPath("/bookings")).content(bookingBody(slotStart, "AA-00-AA", serviceIds)));
    }

    protected static String bookingBody(String slotStart, String plate, String... serviceIds) {
        return """
                {"serviceIds":[%s],"slotStart":"%s","name":"Ana Silva","phone":"912345678",
                 "email":"ana@example.pt","plate":"%s","note":"Barulho ao travar","privacyAccepted":true,
                 "marketingConsent":false,"website":""}""".formatted(quoted(serviceIds), slotStart, plate);
    }

    /// Books the slot and returns the status token.
    protected String bookAndGetToken(Workshop w, String slotStart, String... serviceIds) throws Exception {
        return JsonPath.read(book(w, slotStart, serviceIds).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.statusToken");
    }

    /// The id of the booking with this number, as the front desk lists it (today to +7 days).
    protected String bookingId(Workshop w, int number) throws Exception {
        List<String> ids = JsonPath.read(call(get("/api/v1/bookings"), w.owner()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[?(@.number == " + number + ")].id");
        return ids.getFirst();
    }

    protected ResultActions publicCall(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(fromClient(randomAddress(), request));
    }

    protected static MockHttpServletRequestBuilder fromClient(String address, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON).with(r -> {
            r.setRemoteAddr(address);
            return r;
        });
    }

    protected static String randomAddress() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10.%d.%d.%d".formatted(random.nextInt(256), random.nextInt(256), random.nextInt(1, 255));
    }

    private static String quoted(String... ids) {
        return String.join(",", java.util.Arrays.stream(ids).map(id -> "\"" + id + "\"").toList());
    }
}
