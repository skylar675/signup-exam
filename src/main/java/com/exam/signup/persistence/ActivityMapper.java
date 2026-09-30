package com.exam.signup.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ActivityMapper {

    ActivityRow selectForUpdate(@Param("id") long id);

    ActivityRow selectById(@Param("id") long id);

    Integer selectRemaining(@Param("id") long id);

    int decrementQuota(@Param("id") long id);

    List<ActivityRow> selectPage(@Param("offset") long offset, @Param("limit") int limit);

    long count();
}
