package kit.prolog.repository.custom;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import kit.prolog.domain.*;
import kit.prolog.dto.LayoutDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class LayoutCustomRepositoryImpl implements LayoutCustomRepository {
    private final JPAQueryFactory query;

    private final QMold mold = QMold.mold;
    private final QLayout layout = QLayout.layout;
    private final QContext context = QContext.context;
    private final QImage image = QImage.image;
    private final QCode code = QCode.code1;
    private final QHyperlink hyperlink = QHyperlink.hyperlink;
    private final QMathematics mathematics = QMathematics.mathematics;
    private final QVideo video = QVideo.video;
    private final QDocument document = QDocument.document;

    @Override
    public List<LayoutDto> findLayoutDetailByMold_Id(Long moldId) {

        List<LayoutDto> layoutList = query.select(
                Projections.constructor(LayoutDto.class,
                        layout.id,
                        layout.dtype,
                        layout.coordinateX,
                        layout.coordinateY,
                        layout.width,
                        layout.height
                )
        )
                .from(layout)
                .leftJoin(mold).on(layout.mold.id.eq(mold.id))
                .where(layout.mold.id.eq(moldId))
                .fetch();

        layoutList.forEach(layoutDto -> {
            layoutDto.setContext(
                    selectLayout(layoutDto.getDtype(), layoutDto.getId())
                    .fetchOne()
            );
        });
        return layoutList;
    }

    private JPQLQuery<String> selectLayout(int layoutType, Long layoutId){
        JPQLQuery<String> result;
        switch (layoutType) {
            case 1:
                result = query.select(context.text).from(context).where(context.id.eq(layoutId));
                break;
            case 2:
                result = query.select(image.url).from(image).where(image.id.eq(layoutId));
                break;
            case 3:
                result = query.select(code.code).from(code).where(code.id.eq(layoutId));
                break;
            case 4:
                result = query.select(hyperlink.url).from(hyperlink).where(hyperlink.id.eq(layoutId));
                break;
            case 5:
                result = query.select(mathematics.context).from(mathematics).where(mathematics.id.eq(layoutId));
                break;
            case 6:
                result = query.select(video.url).from(video).where(video.id.eq(layoutId));
                break;
            case 7:
                result = query.select(document.url).from(document).where(document.id.eq(layoutId));
                break;
            default:
                result = null;
                break;
        }
        return result;
    }
}