package com.sage.backend.mapper;

import com.sage.backend.model.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT id, username, password_hash, role, created_at
            FROM app_user
            WHERE username = #{username}
            """)
    AppUser findByUsername(@Param("username") String username);

    @Select("""
            SELECT id, username, password_hash, role, created_at
            FROM app_user
            WHERE id = #{id}
            """)
    AppUser findById(@Param("id") Long id);
}
