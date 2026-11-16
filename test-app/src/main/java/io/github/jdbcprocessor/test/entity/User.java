package io.github.jdbcprocessor.test.entity;

/**
 * User entity — <b>convention case</b>.
 *
 * <p>The names of the {@code private} fields match the SQL column names exactly.
 * The primary key is named {@code id}. No persistence annotation of any kind.</p>
 *
 * <pre>
 * CREATE TABLE users (
 *     id       BIGINT AUTO_INCREMENT PRIMARY KEY,
 *     username VARCHAR(100) NOT NULL,
 *     email    VARCHAR(200) NOT NULL,
 *     age      INT
 * );
 * </pre>
 */
public class User {

    private Long    id;
    private String  username;
    private String  email;
    private Integer age;

    public User() {}

    public User(String username, String email, Integer age) {
        this.username = username;
        this.email    = email;
        this.age      = age;
    }

    public Long    getId()       { return id; }
    public String  getUsername() { return username; }
    public String  getEmail()    { return email; }
    public Integer getAge()      { return age; }

    public void setId(Long id)        { this.id = id; }
    public void setUsername(String u) { this.username = u; }
    public void setEmail(String e)    { this.email = e; }
    public void setAge(Integer a)     { this.age = a; }

    @Override public String toString() {
        return "User{id=" + id + ", username='" + username +
               "', email='" + email + "', age=" + age + '}';
    }
}
