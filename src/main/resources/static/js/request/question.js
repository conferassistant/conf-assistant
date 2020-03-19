$(document).ready(function() {
    $("#QuestionForm").submit(function(event) {
        // Prevent the form from submitting via the browser.
        event.preventDefault();
        askQuestion();
    });

    $("#refresh").click(function () {
        show();
    });

    show();
/*        setInterval('show()',1000);*/
});

function askQuestion(){
    // PREPARE FORM DATA
    var formData = {
        question : $("#question").val()
    };

    // DO POST
    $.ajax({
        type : "POST",
        contentType : "application/json",
        url : "http://localhost:8080/save-question/" + $("#topicId").val(),
        data : JSON.stringify(formData),
        dataType : 'json',
        success : function () {
            show()
        }

    });
    resetData();
    function resetData(){
        $("#question").val("");
    }
}

function show() {
    $.ajax({
        type : "GET",
        url : "http://localhost:8080/get-all-questions/" + $("#topicId").val(),
        cache: false,
        success: function(result){
            $('#getResultDiv ul').empty();
            $.each(result, function(i, question){
                var k = i + 1;
                var variable =
                    "<li>"+ k + "<span>" + '. ' + "</span>" + question.question + "<button id='" + i + "' class='btn-secondary float-right rounded' style='margin-right: 25px'>" + "<i class=\"fa fa-thumbs-up\" style='margin-right: 5px' aria-hidden=\"true\"></i>" + question.rating + "</button>" + "</li>" + "<br>";

                $.each(question.likesSet, function(j, user){

                        if (user.email === $("#currentGuest").val()) {
                            variable =
                                "<li>" + k + "<span>" + '. ' + "</span>" + question.question + "<button id='" + i + "' class='btn-danger float-right rounded' style='margin-right: 25px'>" + "<i class=\"fa fa-thumbs-up\" style='margin-right: 5px' aria-hidden=\"true\"></i>" + question.rating + "</button>" + "</li>" + "<br>";
                        }
                    });

                $('#getResultDiv .list-group').append(variable);
                $('#' + i).click(function () {
                    sendLike(i, question);
                });
            });
        },
        error : function(e) {
            $("#getResultDiv").html("<strong>Error</strong>");
            console.log("ERROR: ", e);
        }
    });
}

function sendLike(i, question) {
    $.ajax({
        type : "GET",
        contentType : "application/json",
        url : "http://localhost:8080/like/" + question.questionId,
        dataType : 'json',
        success : function(isLike) {
            show()
        }
    });
}