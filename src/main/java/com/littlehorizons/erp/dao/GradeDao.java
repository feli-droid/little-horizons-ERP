package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.Grade;

import java.util.List;

/** Read access to the grades catalog. */
public interface GradeDao {

    List<Grade> findAll();
}