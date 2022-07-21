package kit.prolog.domain;

import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity(name = "CONTEXTS")
@DiscriminatorValue("1")
@NoArgsConstructor
public class Context extends Layout{
    @Column(length = 2000)
    private String text;

    public Context(String text) {
        this.text = text;
    }
}
