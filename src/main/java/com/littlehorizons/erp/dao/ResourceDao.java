package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.Resource;
import com.littlehorizons.erp.model.ResourceCategory;
import com.littlehorizons.erp.model.ResourceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence operations for the resources inventory. */
public interface ResourceDao {

    List<Resource> findAll();

    Optional<Resource> findByCode(String code);

    /** Searches by code or by any part of the name. */
    List<Resource> search(String term);

    List<ResourceCategory> findCategories();

    /** @return the generated resource id */
    int insert(String code, String name, int categoryId, String description, LocalDate acquisitionDate, String location);

    boolean updateStatus(int resourceId, ResourceStatus status);
}