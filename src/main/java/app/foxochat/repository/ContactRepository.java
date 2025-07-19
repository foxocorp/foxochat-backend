package app.foxochat.repository;

import app.foxochat.model.User;
import app.foxochat.model.UserContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<UserContact, Long> {

    List<UserContact> findAllByUserId(long id);
}
