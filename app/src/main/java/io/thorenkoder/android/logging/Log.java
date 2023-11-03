package io.thorenkoder.android.logging;

public class Log {

  private CharSequence mTag;
  private CharSequence mMessage;

  public Log(CharSequence tag, CharSequence message) {
    mTag = tag;
    mMessage = message;
  }

  public CharSequence getTag() {
    return mTag;
  }

  public CharSequence getMessage() {
    return mMessage;
  }
}
