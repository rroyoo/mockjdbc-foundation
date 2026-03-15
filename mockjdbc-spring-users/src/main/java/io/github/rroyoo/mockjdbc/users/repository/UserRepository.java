package io.github.rroyoo.mockjdbc.users.repository;

import io.github.rroyoo.mockjdbc.users.domain.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final String SQL_SELECT_ALL = "select id, name, email from users order by id";
    private static final String SQL_SELECT_BY_ID = "select id, name, email from users where id = ?";
    private static final String SQL_SELECT_BY_EMAIL = "select id, name, email from users where email = ?";
    private static final String SQL_INSERT = "insert into users(name, email) values (?, ?)";
    private static final String SQL_UPDATE = "update users set name = ?, email = ? where id = ?";
    private static final String SQL_DELETE = "delete from users where id = ?";

    private static final RowMapper<User> USER_ROW_MAPPER = (rs, rowNum) ->
            new User(rs.getLong("id"), rs.getString("name"), rs.getString("email"));

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<User> findAll() {
        return jdbcTemplate.query(SQL_SELECT_ALL, USER_ROW_MAPPER);
    }

    public Optional<User> findById(long id) {
        return jdbcTemplate.query(SQL_SELECT_BY_ID, USER_ROW_MAPPER, id).stream().findFirst();
    }

    public User create(String name, String email) {
        jdbcTemplate.update(SQL_INSERT, name, email);

        return jdbcTemplate.query(SQL_SELECT_BY_EMAIL, USER_ROW_MAPPER, email)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("User was inserted but could not be read back"));
    }

    public Optional<User> update(long id, String name, String email) {
        int updatedRows = jdbcTemplate.update(SQL_UPDATE, name, email, id);
        if (updatedRows == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    public boolean delete(long id) {
        return jdbcTemplate.update(SQL_DELETE, id) > 0;
    }
}
