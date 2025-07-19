package app.foxochat.service.impl;

import app.foxochat.model.UserContact;
import app.foxochat.repository.ContactRepository;
import app.foxochat.service.ContactService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ContactServiceImpl implements ContactService {

    private final ContactRepository contactRepository;

    @Autowired
    public ContactServiceImpl(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    public List<UserContact> findAllByUserId(long id) {
        return contactRepository.findAllByUserId(id);
    }
}
