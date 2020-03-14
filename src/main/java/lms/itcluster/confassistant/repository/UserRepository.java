package lms.itcluster.confassistant.repository;

import lms.itcluster.confassistant.entity.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends CrudRepository<User, Integer> {

    User findByEmail(String email);

    List<User> findAll();
}
