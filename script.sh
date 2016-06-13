eval 'set +o history' 2>/dev/null || setopt HIST_IGNORE_SPACE 2>/dev/null
touch ~/.gitcookies
chmod 0600 ~/.gitcookies

git config --global http.cookiefile ~/.gitcookies

tr , \\t <<\__END__ >>~/.gitcookies
android.googlesource.com,FALSE,/,TRUE,2147483647,o,git-jessef.quicinc.com=1/hccw7i6AKOJNC4fBW0D6Useg9fd2A4yKSsHvshpeDLk
android-review.googlesource.com,FALSE,/,TRUE,2147483647,o,git-jessef.quicinc.com=1/hccw7i6AKOJNC4fBW0D6Useg9fd2A4yKSsHvshpeDLk
__END__
eval 'set -o history' 2>/dev/null || unsetopt HIST_IGNORE_SPACE 2>/dev/null

