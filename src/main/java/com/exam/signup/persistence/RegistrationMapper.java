package com.exam.signup.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RegistrationMapper {

    RegistrationRow findByUserAndRequest(@Param("userId") long userId, @Param("requestId") String requestId);

    RegistrationRow findByUserAndActivity(@Param("userId") long userId, @Param("activityId") long activityId);

    RegistrationRow findByIdAndUser(@Param("id") long id, @Param("userId") long userId);

    List<RegistrationRow> selectPageByUser(@Param("userId") long userId,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);

    long countByUser(@Param("userId") long userId);

    int insert(RegistrationRow row);
}
