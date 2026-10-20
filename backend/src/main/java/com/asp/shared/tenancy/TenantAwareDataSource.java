package com.asp.shared.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Sets the PostgreSQL session setting {@code app.tenant_id} on every connection handed out,
 * so row-level security policies only ever see the current workshop's rows.
 * With no tenant bound the setting is cleared and tenant tables return nothing.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bindTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bindTenant(super.getConnection(username, password));
    }

    private Connection bindTenant(Connection connection) throws SQLException {
        String tenant = TenantContext.current().map(Object::toString).orElse("");
        try (PreparedStatement ps = connection.prepareStatement("select set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant);
            ps.execute();
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
