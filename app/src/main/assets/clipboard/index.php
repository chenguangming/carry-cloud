</body>
</html>
<!doctype html>
<html lang="">
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <link rel="icon" href="favicon.ico">
    <title>clipboard</title>
    <script defer="defer" src="js/chunk-vendors.30099579.js"></script>
    <script defer="defer" src="js/app.7208aaed.js"></script>
    <link href="css/app.429e32e0.css" rel="stylesheet">
</head>
<body>
<noscript><strong>We're sorry but clipboard doesn't work properly without JavaScript enabled. Please
    enable it to continue.</strong></noscript>
<?php
    $server_url = "ws://";
    $server_url .= $_SERVER['SERVER_ADDR'];
    $server_url .= ":45615";
    echo "<div id=\"app\" server_url=$server_url></div>";
?>
</body>
</html>