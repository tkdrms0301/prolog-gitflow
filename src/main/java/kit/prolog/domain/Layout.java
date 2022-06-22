package kit.prolog.domain;

import org.hibernate.annotations.ColumnDefault;

import javax.persistence.*;

@Entity(name = "LAYOUTS")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(discriminatorType = DiscriminatorType.INTEGER)
public class Layout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LAYOUT_ID", nullable = false)
    private Long id;

    private Double coordinateX;
    private Double coordinateY;
    private Double width;
    private Double height;
    private String explanation;
    @Column(nullable = false)
    @ColumnDefault(value = "false")
    private Boolean main;       // 대표 레이아웃

    @ManyToOne(fetch = FetchType.LAZY)
    private Mold mold;
}