package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.dao.ResourceDao;
import com.littlehorizons.erp.exception.BusinessException;
import com.littlehorizons.erp.model.Resource;
import com.littlehorizons.erp.model.ResourceCategory;
import com.littlehorizons.erp.model.ResourceStatus;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Business rules for the resources inventory. */
public class ResourceController {

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9-]{3,50}$");
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 150;

    private final ResourceDao resourceDao;

    public ResourceController(ResourceDao resourceDao) {
        this.resourceDao = resourceDao;
    }

    public List<Resource> listResources() {
        return resourceDao.findAll();
    }

    public List<Resource> searchResources(String term) {
        return resourceDao.search(term.trim());
    }

    public List<ResourceCategory> listCategories() {
        return resourceDao.findCategories();
    }

    public Resource getByCode(String code) throws BusinessException {
        return resourceDao.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException("No existe un recurso con el código " + code.trim().toUpperCase() + "."));
    }

    public void validateNewCode(String code) throws BusinessException {
        if (!CODE.matcher(code.trim()).matches()) {
            throw new BusinessException("El código debe tener de 3 a 50 letras, números o guiones (ej. LIB-013).");
        }
        if (resourceDao.findByCode(code.trim().toUpperCase()).isPresent()) {
            throw new BusinessException("Ya existe un recurso con el código " + code.trim().toUpperCase() + ".");
        }
    }

    public void validateName(String name) throws BusinessException {
        int length = name.trim().length();
        if (length < MIN_NAME_LENGTH || length > MAX_NAME_LENGTH) {
            throw new BusinessException("El nombre debe tener entre " + MIN_NAME_LENGTH + " y " + MAX_NAME_LENGTH + " caracteres.");
        }
    }

    public void validateAcquisitionDate(LocalDate date) throws BusinessException {
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException("La fecha de adquisición no puede ser futura.");
        }
    }

    public int registerResource(String code, String name, ResourceCategory category, String description,
                                LocalDate acquisitionDate, String location) throws BusinessException {
        validateNewCode(code);
        validateName(name);
        if (acquisitionDate != null) {
            validateAcquisitionDate(acquisitionDate);
        }
        return resourceDao.insert(code.trim().toUpperCase(), name.trim(), category.id(), description,
                acquisitionDate, location);
    }

    /**
     * Changes the status by hand (maintenance, discard or back to available). A lent resource cannot be changed
     * until it is returned, a discarded one is final, and "Prestado" is only set by registering a loan.
     */
    public void changeStatus(Resource resource, ResourceStatus newStatus) throws BusinessException {
        if (resource.status() == ResourceStatus.ON_LOAN) {
            throw new BusinessException("El recurso está prestado: registre primero su devolución.");
        }
        if (resource.status() == ResourceStatus.DISCARDED) {
            throw new BusinessException("El recurso fue dado de baja y su estado ya no se puede cambiar.");
        }
        if (newStatus == ResourceStatus.ON_LOAN) {
            throw new BusinessException("El estado Prestado se asigna al registrar un préstamo.");
        }
        if (resource.status() == newStatus) {
            throw new BusinessException("El recurso ya está en estado " + newStatus.getDbValue() + ".");
        }
        if (!resourceDao.updateStatus(resource.id(), newStatus)) {
            throw new BusinessException("No se pudo cambiar el estado del recurso.");
        }
    }

    /** Counts resources by status (uses streams to group and count). */
    public Map<ResourceStatus, Long> countByStatus(List<Resource> resources) {
        return resources.stream().collect(Collectors.groupingBy(Resource::status,
                () -> new EnumMap<>(ResourceStatus.class), Collectors.counting()));
    }
}