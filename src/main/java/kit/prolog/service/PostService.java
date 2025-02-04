package kit.prolog.service;

import kit.prolog.domain.*;
import kit.prolog.dto.*;
import kit.prolog.enums.LayoutType;
import kit.prolog.repository.jpa.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/*
* 게시글 API 비즈니스 로직
* 게시글 CRUD, 좋아요/취소, 다양한 화면에서의 목록 조회
* */
@Transactional
@Service
@Log4j2
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final MoldRepository moldRepository;
    private final LayoutRepository layoutRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AttachmentRepository attachmentRepository;
    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final CommentRepository commentRepository;
    private final HitRepository hitRepository;
    private final ContextRepository contextRepository;

    private final int POST_WRITE = 1;
    private final int POST_UPDATE = 2;

    /**
     * 레이아웃 작성 API
     * 게시글에 포함되는 레이아웃의 틀과 하위 레이아웃들을 저장
     * 매개변수 : userId(사용자 pk), List<LayoutDto> (레이아웃 데이터), arg(레이아웃틀 이름/가변 매개)
     * 반환 : List<LayoutDto> pk를 포함한 저장된 결과 반환
     * */
    public MoldWithLayoutsDto saveLayouts(Long userId, List<LayoutDto> layoutData, String moldName){
        Mold savedMold = null;
        if (!moldName.isEmpty()) {
            savedMold = moldRepository.save(new Mold(moldName, new User(userId)));
        }
        List<Layout> layouts = layoutData.stream().map(Layout::new).collect(Collectors.toList());
        if (savedMold != null) {
            Mold finalSavedMold = savedMold;
            layouts.forEach(layout -> layout.setMold(finalSavedMold));
        }
        List<LayoutDto> savedLayouts = layoutRepository.saveAll(layouts).stream().map(LayoutDto::new).collect(Collectors.toList());

        MoldWithLayoutsDto result = new MoldWithLayoutsDto(savedLayouts);
        if (savedMold != null) {
            result.setLayoutId(savedMold.getId());
            result.setTitle(savedMold.getName());
        }
        return result;
    }

    /**
    * 레이아웃 리스트 조회 API
    * 매개변수 : userId(회원 pk), moldId(레이아웃 틀 pk)
    * 반환 : MoldWithLayoutsDto (레이아웃 틀과 하위 레이아웃들을 포함한 레이아웃 틀)
    * */
    public MoldWithLayoutsDto viewLayoutsByMold(Long userId, Long moldId) throws NullPointerException, AccessDeniedException{
        if (!checkMoldPermissions(userId, moldId)) throw new AccessDeniedException("No Permissions");

        Optional<Mold> mold = moldRepository.findById(moldId);
        if(mold.isEmpty()) throw new NullPointerException("No Mold Data");
        List<LayoutDto> layoutDtos = layoutRepository.findLayoutDtoByMold_Id(moldId);
        return new MoldWithLayoutsDto(mold.get(), layoutDtos);
    }

    /**
    * 레이아웃 틀 리스트 조회 API
    * 매개변수 : userId(회원 pk)
    * 반환 : List<MoldDto>
    * */
    public List<MoldDto> viewMyMolds(Long userId){
        return moldRepository.findByUser_Id(userId);
    }

    /**
     * 레이아웃 틀 삭제 API
     * 매개변수 : moldId(레이아웃 틀 pk), userId(회원 pk)
     * 반환 : boolean
     * 에러처리 : 해당 회원의 mold가 아닐 경우
     * */
    public void deleteMold(Long moldId, Long userId) throws NullPointerException, AccessDeniedException{
        if (!checkMoldPermissions(userId, moldId)) throw new AccessDeniedException("No Permissions");

        Optional<Mold> mold = moldRepository.findById(moldId);
        if(mold.isEmpty())  throw new NullPointerException("No Appropriate Data");
        List<Post> postList = postRepository.findByMold_Id(moldId);
        List<Layout> layoutList = layoutRepository.findByMold_Id(moldId);
        postList.forEach(post -> {
            post.setMold(null);
        });
        layoutList.forEach(layout -> {
            layout.setMold(null);
        });
        postRepository.saveAllAndFlush(postList);
        layoutRepository.saveAllAndFlush(layoutList);
        moldRepository.delete(mold.get());
    }

    /**
     * 게시글 작성 API 비즈니스 로직
     * 매개변수 : userId(사용자 pk), moldId(레이아웃 틀 pk),
     *          title(게시글 제목), layouts(레이아웃 데이터 리스트), categoryid(카테고리 pk),
     *          param(태그 또는 첨부파일)
     * 반환 : Long(게시글 pk)
     * 에러처리 :
     * */
    public Long writePost(Long userId, String title,
                          List<LayoutDto> layoutDtos, Long categoryId,
                          HashMap<String, Object> param) throws NullPointerException, IllegalArgumentException{

        Optional<User> user = userRepository.findById(userId);
        Optional<Mold> mold;
        Optional<Category> category = categoryRepository.findById(categoryId);
        if(user.isEmpty() || category.isEmpty()) throw new NullPointerException("No Required Data");
        Post post = new Post(title, LocalDateTime.now(),user.get(), category.get());

        if (param.containsKey("moldId")){
            Long moldId = Long.parseLong(param.get("moldId").toString());
            mold = moldRepository.findById(moldId);
            post.setMold(mold.get());
        }

        Post savedPost = postRepository.save(post);
        setMainLayout(layoutDtos);
        writeContexts(layoutDtos, savedPost);
        saveOption(param, savedPost, POST_WRITE);

        return savedPost.getId();
    }

    /**
    * 특정 카테고리 게시글 조회 API
    * 매개변수 : account(사용자 계정), categoryName(카테고리명), cursor(마지막 게시글 pk)
    * 반환 : List<PostPreviewDto>
    * */
    public List<PostPreviewDto> viewPostsByCategory(String account, String categoryName, int cursor){
        return postRepository.findPostByCategoryName(account, categoryName, cursor);
    }

    /**
    * 게시글 상세조회 API
    * 매개변수 : postId(게시글 pk)
    * 반환 : PostDetailDto = 회원정보(작성자 닉네임, 이미지), 게시글정보(pk,제목,작성일자),
    *                       레이아웃틀 pk, 레이아웃 리스트(pk, type, x/y 좌표, 가로/세로, explanation, content),
    *                       카테고리(pk, name), 첨부파일 리스트(pk,이름,url), 태그 리스트(이름),
    *                       조회수, 좋아요(count, exist), 댓글(id, 작성자, 내용, 작성일자, 상위댓글, block 여부)
    * 에러처리 :
    * QueryDSL : 게시글을 기준으로 카테고리, 회원, 레이아웃 틀, 조회수 데이터를 조회
    * Spring JPA : 댓글, 좋아요 , 첨부파일(리스트), 태그(리스트), 레이아웃(리스트)는 별도 쿼리로 조회하여 전달
    * */
    public PostDetailDto viewPostDetailById(Long userId, Long postId) throws NullPointerException{
        Hit savedHit = hitRepository.save(new Hit(LocalDateTime.now(), new Post(postId)));
        PostDetailDto postDetailDto = postRepository.findPostById(postId);
        if (postDetailDto == null) throw new NullPointerException("No Post Data");
        boolean exist;
        int likeCount = likeRepository.countByPost_Id(postId);
        Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "time"));

        List<AttachmentDto> attachmentList = attachmentRepository.findByPost_Id(postId);
        List<String> tagList = new ArrayList<>();
        List<String> optionalTagName = postTagRepository.findTagNameByPost_Id(postId);
        if(!optionalTagName.isEmpty())
            tagList.addAll(optionalTagName);

        //레이아웃 가져오기
        List<LayoutDto> layoutList = postRepository.selectDetailLayout(postId);
        Map<Long, LayoutDto> layoutId = new HashMap<>();
        layoutList.forEach(layout -> {
            if(layout.getDtype() == LayoutType.IMAGE.getValue()){
                if(layoutId.containsKey(layout.getId())){
                    layoutId.get(layout.getId()).addUrl(layout);
                }else {
                    layoutId.put(layout.getId(), layout);
                }
            }else{
                layoutId.put(layout.getId(), layout);
            }
        });

        LikeDto like = new LikeDto(likeCount);
        if (userId != null) {
            exist = likeRepository.existsByUser_IdAndPost_Id(userId, postId);
            like.setExist(exist);
        }
        postDetailDto.setLikeDto(like);
        postDetailDto.setAttachmentDto(attachmentList);
        postDetailDto.setTags(tagList);
        postDetailDto.setLayoutDto(new ArrayList<>(layoutId.values()));

        return postDetailDto;
    }


    /**
     * 게시글 수정 API
     * 매개변수 :
     * 반환 :
     * */
    public Long updatePost(Long postId, Long userId, String title,
                              List<LayoutDto> layoutDtos, Long categoryId,
                              HashMap<String, Object> param) throws AccessDeniedException{

        if (!checkPostPermissions(userId, postId)) throw new AccessDeniedException("No Permissions");
        Optional<Mold> mold;
        Optional<Category> category = categoryRepository.findById(categoryId);

        Post post = postRepository.findById(postId).get();
        post.setTitle(title);
        post.setCategory(category.get());

        if (param.containsKey("moldId")){
            Long moldId = Long.parseLong(param.get("moldId").toString());
            mold = moldRepository.findById(moldId);
            post.setMold(mold.get());
        }
        Post savedPost = postRepository.save(post);

        setMainLayout(layoutDtos);
        writeContexts(layoutDtos, savedPost);
        saveOption(param, savedPost, POST_UPDATE);

        return savedPost.getId();
    }

    /**
    * 게시글 삭제 API
    * 매개변수 : postId(게시글 pk), userId(회원 pk)
    * 반환 : boolean
    * 발생 가능 에러 : IllegalArg, SQL Error(?)
    * */
    public void deletePost(Long postId, Long userId) throws AccessDeniedException, NullPointerException{
        if (!checkPostPermissions(userId, postId)) throw new AccessDeniedException("No Permissions");
        Optional<Post> post = postRepository.findById(postId);
        if(post.isEmpty())  throw new NullPointerException("No Post Data");

        likeRepository.deleteAllByPost_Id(postId);
        commentRepository.deleteAllByPost_Id(postId);
        hitRepository.deleteAllByPost_Id(postId);
        attachmentRepository.deleteAllByPost_Id(postId);
        postTagRepository.deleteAllByPost_Id(postId);
        contextRepository.deleteAllByPost_Id(postId);
        postRepository.deleteById(postId);
    }

    /**
     * 태그 조회 API
     * 매개변수 : tagName(태그 이름)
     * 반환 : List<String>
     * 발생 가능 에러 :
     * */
    public List<String> findTagByName(String tagName){
        return tagRepository.findByNameStartingWith(tagName)
                .stream().map(Tag::getName).collect(Collectors.toList());
    }

    /**
    * 게시글 좋아요 API
    * 매개변수 : postId(게시글 pk), userId(사용자 pk)
    * 반환 : boolean
    * 발생 가능 에러 : DataIntegrityViolationException(잘못된 데이터가 바인딩 되었을 때 발생)
    * */
    public boolean likePost(Long userId, Long postId) throws NullPointerException{
        if (postRepository.findById(postId).isEmpty()) throw new NullPointerException("No Such Post");
        Optional<Like> like = likeRepository.findByUser_IdAndPost_Id(userId, postId);
        if (like.isPresent()) {
            likeRepository.delete(like.get());
        } else {
            Like myLike = new Like(new User(userId), new Post(postId));
            likeRepository.save(myLike);
        }
        return true;
    }

    /**
     * 파일 업로드 API
     * 업로드 후 정보를 토대로 DB에 저장
     * 매개변수 : List<FileDto>
     * 반환 : List<String> URL List
     * */
    public List<AttachmentDto> saveUploadedFiles(List<FileDto> files){
        List<Attachment> uploadedFiles = files.stream().map(Attachment::new).collect(Collectors.toList());
        List<Attachment> attachments = attachmentRepository.saveAll(uploadedFiles);
        return attachments.stream().map(AttachmentDto::new).collect(Collectors.toList());
    }

    /**
     * 파일 삭제 API
     * 매개변수 : String
     * 반환 : String
     * */
    public String deleteFile(String fileName){
        Optional<Attachment> attachment = attachmentRepository.findByName(fileName);
        attachment.ifPresent(attachmentRepository::delete);
        return attachment.isPresent() ? attachment.get().getName() : "";
    }

    /**
     * 내가 쓴 글 목록 조회 API
     * 매개변수 : userId(사용자 pk), cursor(페이지 번호)
     * 반환 : List<PostPreviewDto>
     * */
    public List<PostPreviewDto> getMyPostList(String account, int cursor){
        return postRepository.findMyPostByUserId(account, cursor);
    }
    public List<PostPreviewDto> getMyPostList(Long userId, int cursor){
        return postRepository.findMyPostByUserId(userId, cursor);
    }

    /**
     * 좋아요 한 글 목록 조회 API
     * 매개변수 : userId(사용자 pk), account(계정명), cursor(페이지 번호)
     * 반환 : List<PostPreviewDto>
     * */
    public List<PostPreviewDto> getLikePostList(Long userId, String account, int cursor){
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) throw new NullPointerException("No User Data");
        if (!user.get().getAccount().equals(account)) throw new IllegalArgumentException("No Permission");
        return postRepository.findLikePostByAccount(account, cursor);
    }
    public List<PostPreviewDto> getLikePostList(Long userId, int cursor){
        return postRepository.findLikePostByAccount(userId, cursor);
    }

    /**
     * 전체 게시글 목록 조회 API
     * 최근 날짜동안의 좋아요 상승률 내림차순으로 게시글 검색
     * 매개변수 : cursor(페이지 번호)
     * 반환 : List<PostPreviewDto>
     * */
    public List<PostPreviewDto> getHottestPostList(int cursor){
        return postRepository.findHottestPosts(cursor);
    }

    /**
     * 최근 게시글 목록 조회 API
     * 매개변수 : cursor(페이지 번호)
     * 반환 : List<PostPreviewDto>
     * */
    public List<PostPreviewDto> getRecentPostList(int cursor){
        return postRepository.findRecentPosts(cursor);
    }

    /**
     * 게시글 검색 API
     * 매개변수 : keyword(검색 키워드), cursor(페이지 번호)
     * 반환 : List<PostPreviewDto>
     * */
    public List<PostPreviewDto> searchPosts(String keyword, int cursor){
        return postRepository.searchPosts(keyword, cursor);
    }

    /**
     * 권한 검사
     * 작성자와 요청자의 pk 비교
     * */
    private boolean checkPostPermissions(Long userId, Long postId){
        return postRepository.checkPostWriter(postId).equals(userId);
    }
    private boolean checkMoldPermissions(Long userId, Long moldId){
        return postRepository.checkMoldWriter(moldId).equals(userId);
    }

    /**
     * 게시글 작성&수정
     * Layout 리스트에서 main 설정이 없다면 첫번째 Layout으로 임의 설정
     * */
    private void setMainLayout(List<LayoutDto> layoutDtos){
        int mainLayoutCounter = 0;
        final int ADDITION = 1;
        final int NONE = 0;

        for (LayoutDto layoutDto : layoutDtos) {
            mainLayoutCounter += layoutDto.getLeader() ? ADDITION : NONE;
            if(mainLayoutCounter > ADDITION) throw new IllegalArgumentException("Too Many Main Layout");
        }
        if(mainLayoutCounter == NONE){
            layoutDtos.get(NONE).setLeader(true);
        }
    }
    /**
     * 게시글 작성 & 수정
     * Layout과 Context 저장
     * */
    private void writeContexts(List<LayoutDto> layoutDtos, Post savedPost){
        layoutDtos.forEach(layoutDto -> {
            layoutRepository.findLayoutById(layoutDto.getId()).ifPresent(layout -> {
                layout.setExplanation(layoutDto.getExplanation());
                layout.setCoordinateX(layoutDto.getCoordinateX());
                layout.setCoordinateY(layoutDto.getCoordinateY());
                layout.setWidth(layoutDto.getWidth());
                layout.setHeight(layoutDto.getHeight());

                LayoutType layoutType = LayoutType.values()[layout.getDtype()];

                List<Context> contextList = new ArrayList<>();
                Context context = new Context(layoutDto.getLeader(), savedPost, layout);
                switch (layoutType){
                    case CONTEXT:
                    case MATHEMATICS:
                        context.setContext(layoutDto.getContent());
                        break;
                    case IMAGE:
                        layoutDto.getUrl().forEach(url -> {
                            contextList.add(new Context(url, layoutDto.getLeader(), savedPost, layout));
                        });
                        break;
                    case CODES:
                        List<String> codes = layoutDto.getCodes();
                        context.setCode(codes.get(0));
                        context.setCodeExplanation(codes.get(1));
                        context.setCodeType(codes.get(2));
                        break;
                    case HYPERLINK:
                    case VIDEOS:
                    case DOCUMENTS:
                        context.setUrl(layoutDto.getContent());
                        Optional<Attachment> optional = attachmentRepository.findByUrl(layoutDto.getContent());
                        optional.ifPresent(attachment -> {
                            attachment.setPost(savedPost);
                            attachmentRepository.save(attachment);
                        });
                        break;
                }
                layoutRepository.save(layout);
                if(contextList.isEmpty()){
                    contextRepository.save(context);
                }else{
                    contextList.forEach(contextRepository::save);
                }
            });
        });
    }

    /**
     * 게시글 작성&수정
     * 태그와 첨부파일 저장
     * */
    private void saveOption(HashMap<String, Object> param, Post savedPost, final int methodType){
        if (!param.isEmpty()){
            if(param.containsKey("tags")){
                List<String> tagList = (List<String>) param.get("tags");
                if(methodType == POST_UPDATE)   postTagRepository.deleteAllByPost_Id(savedPost.getId());
                tagList.forEach(tag -> {
                    Optional<Tag> optionalTag = tagRepository.findByName(tag);
                    if(!optionalTag.isPresent()) {
                        optionalTag = Optional.of(tagRepository.save(new Tag(tag)));
                    }
                    PostTag postTag = new PostTag(savedPost, optionalTag.get());
                    postTagRepository.save(postTag);
                });
            }
            if(param.containsKey("attachment")){
                List<AttachmentDto> attachmentDtos = (List<AttachmentDto>) param.get("attachment");
                attachmentDtos.forEach(attachment -> {
                    Optional<Attachment> optional = attachmentRepository.findByName(attachment.getName());
                    if(optional.isPresent()) {
                        optional.get().setPost(savedPost);
                        attachmentRepository.save(optional.get());
                    }
                });
            }
        }
    }
}
