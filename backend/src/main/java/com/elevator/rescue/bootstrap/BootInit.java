package com.elevator.rescue.bootstrap;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * 启动初始化（不经过 JPA）：用单条 JDBC 连接、单个事务完成「建表 + 种子电梯台账」。
 * SQLite 同一时刻只允许一个写者；启动期若存在第二条连接（Hibernate 自动建表、
 * Session.doWork 独立连接、TABLE 序列等）都会触发 SQLITE_BUSY / 连接超时，
 * 因此全部收敛到这里一次性完成。
 */
@ApplicationScoped
public class BootInit {

    @Inject
    DataSource dataSource;

    private record SeedElevator(String code, String building, String location, String vendor) {}

    private static final SeedElevator[] SEEDS = {
            new SeedElevator("EL-1001", "滨江花园 1 号楼", "1 号楼 2 单元东侧客梯", "安迅电梯维保"),
            new SeedElevator("EL-1002", "滨江花园 1 号楼", "1 号楼 2 单元西侧客梯", "安迅电梯维保"),
            new SeedElevator("EL-2001", "云顶大厦", "云顶大厦 B 座高区消防梯", "恒升机电维保"),
            new SeedElevator("EL-2002", "云顶大厦", "云顶大厦 A 座低区客梯", "恒升机电维保")
    };

    void onStart(@Observes StartupEvent ev) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA busy_timeout=5000");
                st.execute("PRAGMA foreign_keys=ON");
                for (String sql : readSchemaSql().split(";")) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        st.execute(trimmed);
                    }
                }
                seedIfEmpty(conn);
            }
            conn.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite 初始化失败: " + e.getMessage(), e);
        }
    }

    private void seedIfEmpty(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from Elevator")) {
            rs.next();
            if (rs.getInt(1) > 0) {
                return;
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "insert into Elevator (code, building, location, maintenanceVendor, status) " +
                        "values (?, ?, ?, ?, 'IN_SERVICE')")) {
            for (SeedElevator s : SEEDS) {
                ps.setString(1, s.code());
                ps.setString(2, s.building());
                ps.setString(3, s.location());
                ps.setString(4, s.vendor());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String readSchemaSql() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("classpath:schema.sql 不存在");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(line -> line.contains("--") ? line.substring(0, line.indexOf("--")) : line)
                        .collect(Collectors.joining("\n"));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取 schema.sql 失败", e);
        }
    }
}
