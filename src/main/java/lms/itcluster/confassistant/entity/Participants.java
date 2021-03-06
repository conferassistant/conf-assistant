package lms.itcluster.confassistant.entity;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "participants")
public class Participants {

    @EmbeddedId
    private ParticipantsKey participantsKey;

    public ParticipantsKey getParticipantsKey() {
        return participantsKey;
    }

    public void setParticipantsKey(ParticipantsKey participantsKey) {
        this.participantsKey = participantsKey;
    }

    public Participants() {
    }
    
    public Participants(User user, Conference conference, ParticipantType participantType){
        ParticipantsKey pk= new ParticipantsKey();
        pk.setUser(user);
        pk.setConference(conference);
        pk.setParticipantType(participantType);
        this.setParticipantsKey(pk);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Participants that = (Participants) o;
        return Objects.equals(participantsKey, that.participantsKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(participantsKey);
    }
}
