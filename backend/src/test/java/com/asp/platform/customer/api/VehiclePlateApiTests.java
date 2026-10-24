package com.asp.platform.customer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.time.YearMonth;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Portuguese plate formats, registration country and month on the vehicle API.
class VehiclePlateApiTests extends ApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
            "aa-12-bc, PT_2020, AA12BC, AA-12-BC",
            "12 AB 34, PT_2005, 12AB34, 12-AB-34",
            "12-34-ab, PT_1992, 1234AB, 12-34-AB",
            "AB-12-34, PT_1937, AB1234, AB-12-34"})
    void should_accept_each_portuguese_layout_and_show_it_with_hyphens(String input, String era, String stored,
            String display) throws Exception {
        String owner = signup("Oficina Matrículas");
        String customerId = createCustomer(owner, "Rita");

        call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"%s"}""".formatted(customerId, input)), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plate").value(stored))
                .andExpect(jsonPath("$.plateDisplay").value(display))
                .andExpect(jsonPath("$.plateEra").value(era))
                .andExpect(jsonPath("$.registrationCountry").value("PT"))
                .andExpect(jsonPath("$.registrationDate").isEmpty());
    }

    @Test
    void should_refuse_an_invalid_portuguese_plate_on_create_and_edit() throws Exception {
        String owner = signup("Oficina Inválida");
        String customerId = createCustomer(owner, "Hugo");

        expectInvalidPlate(call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"ABC-123"}""".formatted(customerId)), owner));
        expectInvalidPlate(call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"1234 BCD","registrationCountry":"pt"}""".formatted(customerId)), owner));

        String vehicleId = createVehicle(owner, customerId, "12-AB-34");
        expectInvalidPlate(call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("{\"plate\":\"12-AB-3\"}",
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner));

        call(get("/api/v1/vehicles/" + vehicleId), owner).andExpect(jsonPath("$.plate").value("12AB34"));
    }

    @Test
    void should_accept_a_free_form_plate_for_a_vehicle_registered_abroad() throws Exception {
        String owner = signup("Oficina Fronteira");
        String customerId = createCustomer(owner, "Iker");

        String vehicleId = idOf(call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"1234 bcd","registrationCountry":"es","registrationDate":"2019-07"}"""
                .formatted(customerId)), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plate").value("1234BCD"))
                .andExpect(jsonPath("$.plateDisplay").value("1234BCD"))
                .andExpect(jsonPath("$.plateEra").isEmpty())
                .andExpect(jsonPath("$.registrationCountry").value("ES"))
                .andExpect(jsonPath("$.registrationDate").value("2019-07")));

        // An edit without a country keeps Spain, so the plate is still free-form.
        call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("{\"plate\":\"5678-XYZ\"}",
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("5678XYZ"))
                .andExpect(jsonPath("$.registrationCountry").value("ES"))
                .andExpect(jsonPath("$.registrationDate").isEmpty());

        // Moving it to Portugal checks the plate against the Portuguese formats.
        expectInvalidPlate(call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("""
                {"plate":"5678XYZ","registrationCountry":"PT"}""",
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner));
        call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("""
                {"plate":"56-xy-78","registrationCountry":"PT","registrationDate":"2012-04"}""",
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plateDisplay").value("56-XY-78"))
                .andExpect(jsonPath("$.plateEra").value("PT_2005"))
                .andExpect(jsonPath("$.registrationCountry").value("PT"))
                .andExpect(jsonPath("$.registrationDate").value("2012-04"));
    }

    @Test
    void should_default_the_registration_country_to_the_workshops_country() throws Exception {
        String owner = signup("Taller Vigo");
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Taller Vigo","country":"ES","timezone":"Europe/Madrid","defaultVatRate":21,
                 "labourRateCents":4000}""",
                 versionAt(owner, "/api/v1/workshop/settings"))), owner).andExpect(status().isOk());

        call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"1234 BCD"}""".formatted(createCustomer(owner, "Lucía"))), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationCountry").value("ES"))
                .andExpect(jsonPath("$.plateEra").isEmpty());
    }

    @Test
    void should_validate_the_registration_month_and_country() throws Exception {
        String owner = signup("Oficina Datas");
        String customerId = createCustomer(owner, "Duarte");
        String nextMonth = YearMonth.now().plusMonths(1).toString();

        call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"12-AB-34","registrationDate":"%s","registrationCountry":"XX"}"""
                .formatted(customerId, nextMonth)), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.registrationDate").exists())
                .andExpect(jsonPath("$.errors.registrationCountry").exists());
        call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"AB-12-34","registrationDate":"1936-12"}""".formatted(customerId)), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.registrationDate").exists());
        call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"AB-12-34","registrationDate":"1965-13"}""".formatted(customerId)), owner)
                .andExpect(status().isBadRequest());

        call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"AB-12-34","registrationDate":"1937-01"}""".formatted(customerId)), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationDate").value("1937-01"));
    }

    @Test
    void should_show_the_plate_fields_on_lists_work_orders_and_the_board() throws Exception {
        String owner = signup("Oficina Quadro");
        String customerId = createCustomer(owner, "Beatriz");
        String vehicleId = idOf(call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"12-ab-34","vin":"VF1ABCDEFGH123456","make":"Renault","model":"Clio",
                 "year":2012,"fuelType":"Diesel","mileage":150000,"registrationDate":"2012-04"}"""
                .formatted(customerId)), owner).andExpect(status().isCreated()));

        String body = call(get("/api/v1/vehicles/" + vehicleId), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("12AB34"))
                .andExpect(jsonPath("$.plateDisplay").value("12-AB-34"))
                .andExpect(jsonPath("$.plateEra").value("PT_2005"))
                .andExpect(jsonPath("$.registrationCountry").value("PT"))
                .andExpect(jsonPath("$.registrationDate").value("2012-04"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // A sample response for the API documentation.
        Files.writeString(Path.of("target", "vehicle-pt-example.json"), body);

        call(get("/api/v1/customers/" + customerId + "/vehicles"), owner)
                .andExpect(jsonPath("$[0].plateDisplay").value("12-AB-34"))
                .andExpect(jsonPath("$[0].registrationDate").value("2012-04"));

        String workOrderId = idOf(call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","mileageIn":150100}""".formatted(vehicleId)), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plate").value("12AB34"))
                .andExpect(jsonPath("$.plateDisplay").value("12-AB-34"))
                .andExpect(jsonPath("$.plateEra").value("PT_2005"))
                .andExpect(jsonPath("$.registrationCountry").value("PT"))
                .andExpect(jsonPath("$.registrationDate").value("2012-04")));
        call(get("/api/v1/vehicles/" + vehicleId + "/work-orders"), owner)
                .andExpect(jsonPath("$[0].plateDisplay").value("12-AB-34"));
        call(patch("/api/v1/work-orders/" + workOrderId + "/status").content("{\"status\":\"IN_PROGRESS\"}"), owner)
                .andExpect(status().isOk());

        call(get("/api/v1/work-orders/board"), owner)
                .andExpect(jsonPath("$.columns.IN_PROGRESS[0].plate").value("12AB34"))
                .andExpect(jsonPath("$.columns.IN_PROGRESS[0].plateDisplay").value("12-AB-34"))
                .andExpect(jsonPath("$.columns.IN_PROGRESS[0].plateEra").value("PT_2005"))
                .andExpect(jsonPath("$.columns.IN_PROGRESS[0].registrationCountry").value("PT"))
                .andExpect(jsonPath("$.columns.IN_PROGRESS[0].registrationDate").value("2012-04"));
    }

    @Test
    void should_keep_reading_and_editing_a_plate_saved_before_the_format_check() throws Exception {
        String owner = signup("Oficina Antiga");
        String customerId = createCustomer(owner, "Alberto");
        String vehicleId = createVehicle(owner, customerId, "12-AB-34");
        // A plate typed in before formats were checked.
        int updated = jdbc.execute((java.sql.Connection connection) -> {
            try (PreparedStatement bind = connection.prepareStatement("select set_config('app.tenant_id', ?, false)");
                    PreparedStatement legacy = connection.prepareStatement(
                            "update vehicle set plate = 'ABC123' where id = ?::uuid")) {
                bind.setString(1, tenantOf(owner));
                bind.execute();
                legacy.setString(1, vehicleId);
                return legacy.executeUpdate();
            }
        });
        assertThat(updated).isEqualTo(1);

        call(get("/api/v1/vehicles/" + vehicleId), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("ABC123"))
                .andExpect(jsonPath("$.plateDisplay").value("ABC123"))
                .andExpect(jsonPath("$.plateEra").isEmpty());
        call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("{\"plate\":\"abc 123\",\"make\":\"Fiat\"}",
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.make").value("Fiat"));
        expectInvalidPlate(call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("{\"plate\":\"ABC124\"}",
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner));
    }

    private static void expectInvalidPlate(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/invalid-plate"))
                .andExpect(jsonPath("$.code").value("invalid-plate"))
                .andExpect(jsonPath("$.country").value("PT"))
                .andExpect(jsonPath("$.errors.plate").exists());
    }

    private static String tenantOf(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonPath.read(payload, "$.tenant_id");
    }
}
