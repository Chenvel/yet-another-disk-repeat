package ru.pasha.yetAnotherDiskRepeat.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.pasha.yetAnotherDiskRepeat.domain.SystemItem;
import ru.pasha.yetAnotherDiskRepeat.domain.SystemItemHistory;
import ru.pasha.yetAnotherDiskRepeat.exception.SystemItemNotFoundException;
import ru.pasha.yetAnotherDiskRepeat.exception.ValidationException;
import ru.pasha.yetAnotherDiskRepeat.repository.SystemItemHistoryRepository;
import ru.pasha.yetAnotherDiskRepeat.repository.SystemItemRepository;
import ru.pasha.yetAnotherDiskRepeat.validators.DateParser;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class SystemItemServiceImpl implements SystemItemService {

    private final SystemItemRepository systemItemRepository;
    private final SystemItemHistoryRepository systemItemHistoryRepository;
    private final EntityManager entityManager;
    private final DateParser dateParser;

    @Override
    @Transactional
    public List<SystemItem> saveAll(List<SystemItem> systemItems) {
        for (SystemItem systemItem : systemItems) {
            if (systemItem.getParentId() != null) {
                Optional<SystemItem> parent = systemItemRepository.findById(systemItem.getParentId());

                if (parent.isEmpty()) throw new ValidationException("Validation Failed");
                systemItem.setParent(parent.get());
            }

            Optional<SystemItem> oldSystemItem = systemItemRepository.findById(systemItem.getId());
            oldSystemItem.ifPresent(item -> update(systemItem, item));

            updateDateForParents(systemItem, systemItem.getDate());
            updateSizeForParents(systemItem, systemItem.getSize());

            systemItemRepository.flush();
            systemItemRepository.save(systemItem);
        }

        return systemItems;
    }

    @Override
    public void update(SystemItem newSystemItem, SystemItem oldSystemItem) {
        SystemItemHistory systemItemHistory = new SystemItemHistory(
                oldSystemItem.getUrl(),
                oldSystemItem.getDate(),
                oldSystemItem.getParentId(),
                oldSystemItem.getType(),
                oldSystemItem.getSize()
        );

        if (oldSystemItem.getPreviousVersion() != null) {
            oldSystemItem.getPreviousVersion().addParent(systemItemHistory);
            oldSystemItem.getPreviousVersion().setCurrentVersion(null);
        }

        newSystemItem.setPreviousVersion(systemItemHistory);

        systemItemHistoryRepository.save(systemItemHistory);
    }

    @Transactional
    @Override
    public SystemItem deleteById(String id, String date) {
        Optional<SystemItem> optionalSystemItem = systemItemRepository.findById(id);
        if (optionalSystemItem.isEmpty()) throw new SystemItemNotFoundException("Item not found");

        Date parsedDate = dateParser.parse(date);
        SystemItem systemItem = optionalSystemItem.get();

        updateDateForParents(systemItem, parsedDate);
        updateSizeForParents(systemItem, -systemItem.getSize());
        deleteHistory(systemItem.getPreviousVersion());

        systemItemRepository.delete(systemItem);

        return systemItem;
    }

    @Override
    public void updateDateForParents(SystemItem leaf, Date date) {
        while (leaf.getParent() != null) {
            leaf = leaf.getParent();
            leaf.setDate(date);
        }
    }

    @Override
    public void updateSizeForParents(SystemItem leaf, Long size) {
        while (leaf.getParent() != null) {
            leaf = leaf.getParent();
            leaf.setSize(leaf.getSize() + size);
        }
    }

    @Override
    public void deleteHistory(SystemItemHistory systemItemHistory) {
        if (systemItemHistory != null) {
            systemItemHistoryRepository.delete(systemItemHistory);
        }
    }

    @Override
    public SystemItem findById(String id) {
        Optional<SystemItem> systemItem = systemItemRepository.findById(id);

        if (systemItem.isEmpty()) throw new SystemItemNotFoundException("Item not found");

        return systemItem.get();
    }
}
